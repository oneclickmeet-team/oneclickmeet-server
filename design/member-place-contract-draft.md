# 회원·장소 데이터 모델 및 인증·API 계약 제안 (초안)

- 작성: 승진 (member · place 담당)
- 상태: **제안 단계 — 재원과 합의 전에는 확정이 아님**
- 승진 결정(10/30): 장소는 **A안(`saved_places` 한 테이블)으로 시작**한다. 필요해지면 B안(`places` 분리)으로 옮긴다(§2.2).
- 기준 자료: `main`@3fd3ce9 (#14 머지 후), `docs/conventions.md`, `CODEOWNERS`, 백엔드 TRD 초안 v0.1(연경, 10/14), 기능 명세서 v0.1
- 확인하지 못한 것: TRD v1.0(10/18, 재원 작성)의 API 49개 목록 원문. 아래 API 목록은 TRD v1.0과 대조해야 한다.

---

## 0. 먼저 알아둘 현재 상태

| 항목 | 현재 `main` 상태 | 이 제안에 미치는 영향 |
|---|---|---|
| 공통 응답 | conventions.md가 `ProblemDetail`에서 `success / data / error` 래퍼로 바뀜 | 이 문서의 모든 예시는 래퍼 기준. 전역 에러 핸들러는 #13(재원) 진행 중 |
| 코스 엔티티 | `Course`, `CoursePlace`, `CoursePlaceMenu` 등이 머지됨 | `CoursePlace`는 장소 이름(`place_name`)만 갖고 `Place`/`SavedPlace`와 연결이 없음 → §2.3 |
| 기반 엔티티 | `BaseEntity`(createdAt/updatedAt) 존재 | `Member`, `Place` 등도 이를 상속해 사용 |
| 컨벤션과 코드 차이 | 컨벤션: 테이블 복수형 snake_case, PK는 `IDENTITY`. 코드: `course_place`(단수), `@GeneratedValue`(기본 전략) | 어느 쪽을 따를지 재원과 확인 (§5 질문 1) |
| 인증 | 의존성(Spring Security)만 있고 설정·코드 없음 | §3 |

---

## 1. 담당 범위 (CODEOWNERS)

- 승진: `member`, `place`, `recommendation`, `activity`, `notification`
- 재원: `course`, `couple`, `memo`, `comment`, `global`(설정·예외·`global/entity`), 빌드·CI
- 공동 검토: `*.sql`, 아직 담당이 정해지지 않은 파일, 데이터 모델·요청/응답 계약

→ `Member`와 `SavedPlace`는 승진이 소유하고, **`course`와 `couple`이 이를 참조하는 방향으로만** 의존하게 한다(역방향 의존 금지). 코스는 장소를 직접 참조하지 않고 스냅샷을 가진다(§2.3).

---

## 2. 데이터 모델 제안

### 2.1 `members`

| 컬럼 | 타입/제약 | 비고 |
|---|---|---|
| `id` | BIGINT PK | `IDENTITY` |
| `name` | VARCHAR, NOT NULL | 소셜 프로필 이름 또는 직접 입력 |
| `email` | VARCHAR, NULL 허용 | 소셜 제공자가 이메일을 주지 않을 수 있음 |
| `auth_provider` | ENUM(STRING): `KAKAO`, `GOOGLE` (+ 이메일 가입 확정 시 `EMAIL`) | §3.1에서 범위 확정 |
| `provider_user_id` | VARCHAR, NOT NULL | 제공자 쪽 사용자 식별자 |
| `password_hash` | VARCHAR, NULL | 이메일 가입을 하기로 확정한 경우에만 |
| `created_at`, `updated_at` | `BaseEntity` | |

- 유니크: (`auth_provider`, `provider_user_id`)
- 커플 연결 상태는 `members`에 두지 않고 `couple`(재원)에서 조회한다. `/me` 응답의 연결 상태는 `couple` 쪽 조회 결과를 조합한다.

### 2.2 장소 — A안(`saved_places` 한 테이블)으로 시작

사용자별 보관함 행에 장소 정보(이름·주소·좌표 등)를 함께 저장한다. 같은 장소를 두 사람이 저장하면 각자 행이 생긴다.

`saved_places`: `id`, `member_id`(→ members), `provider`(예: KAKAO), `provider_place_id`, `name`, `address`, `road_address`(NULL), `lat`, `lng`, `category`, `source_url`(NULL), `hours`(NULL), `created_at`, `updated_at`

- 유니크: (`member_id`, `provider`, `provider_place_id`) — 같은 장소 중복 저장 방지(명세서 14장 #16 미결 사항에 대한 제안).
- 저장 시 장소 정보는 앱이 고른 후보의 값을 그대로 받는다. 값이 다른 사용자에게 퍼지지 않으므로 서버가 카카오를 다시 호출해 검증하지 않고, **형식·범위 검증만** 한다(필수값, 길이, 좌표 범위 등).
- 메모는 `memo` 도메인(재원)에서 `saved_places`를 참조해 작성자 본인만 읽도록 한다.
- 공동 보관함(`shared_place`)은 `couple`/`share` 쪽 이슈에서 다룬다. 이 제안 범위 밖.

**이후 B안(`places` + `saved_places` 분리)으로 옮길 때를 대비한 약속**
- 처음부터 `provider`, `provider_place_id`를 별도 컬럼으로 둔다(분리할 때 같은 장소를 묶는 기준이 된다).
- 코스는 `saved_places`를 참조하지 않고 자체 스냅샷을 가진다(§2.3). 그러면 분리 시 코스 쪽 수정이 작다.
- 옮기는 절차(예상): `provider` + `provider_place_id`가 같은 행을 모아 `places`를 만들고, `saved_places.place_id`를 채운 뒤 중복 컬럼을 제거한다. 이 이전은 데이터 변환이 필요하므로, 공유 dev 데이터를 보존하기 시작하기 전(Flyway 도입 판단 시점)에 하는 편이 비용이 작다.

### 2.3 `CoursePlace`와의 연결 (재원 영역 — 변경 요청)

현재 `CoursePlace`는 `place_name`만 있다. 추천 결과의 외부 후보는 보관함에 없을 수 있고, 코스를 저장해도 장소는 보관함에 자동으로 담기지 않는다(기능 명세서). 따라서 코스는 보관함을 참조하지 않고 **저장 시점의 스냅샷**을 직접 가지는 것을 제안한다.

- `CoursePlace`에 `provider`, `provider_place_id`, `address`, `lat`, `lng` 컬럼을 추가한다(기존 `place_name`은 유지). 지도 핀·도보 시간 추정에 좌표가 필요하다.
- 변경은 재원의 도메인이므로 재원이 판단·작업한다(필요하면 내가 필드 정의를 제공).
- 나중에 장소를 분리(B안)하면 `CoursePlace`가 `places.id`를 참조하도록 컬럼 하나를 추가하면 된다.

---

## 3. 인증 계약 제안

### 3.1 먼저 확정할 것

| 항목 | 자료마다 다른 점 | 제안 |
|---|---|---|
| 로그인 방식 | 기능 정의(FR-01)는 이메일·비밀번호 가입, 10/18 결정은 카카오·구글 | **소셜 로그인(카카오·구글)만** 먼저 구현하고, 이메일 가입은 보류. 일정이 빠듯하면 카카오만 먼저 |
| 토큰 | TRD 초안: 토큰 기반, 방식 TBD | Access 토큰 + Refresh 토큰. 형식(JWT 등)과 만료 시간은 미정 |
| 소셜 로그인 흐름 | 미정(PKCE 언급) | 앱이 제공자 SDK로 증명(토큰)을 받고 서버가 제공자에게 검증한 뒤 자체 토큰 발급 |

### 3.2 API 후보 (TRD v1.0 목록과 대조 필요)

| 구분 | 메서드·경로 | 설명 |
|---|---|---|
| 인증 | `POST /auth/login/{provider}` | 소셜 증명으로 로그인, 신규면 가입 처리. 응답: 토큰 + 회원 요약 |
| 인증 | `POST /auth/refresh` | Refresh 토큰으로 Access 토큰 재발급 |
| 인증 | `POST /auth/logout` | Refresh 토큰 폐기 |
| 계정 | `GET /members/me` | 이름·이메일·연결 상태(`none/linked/grace`) |
| 계정 | `PATCH /members/me` | 프로필 수정 |
| 계정 | `DELETE /members/me` | 탈퇴(커플 연결·공동 데이터 처리 정책은 재원과 합의 필요) |

> 이 표는 "인증 5개 + 계정 2개"라는 개수와 맞지 않는다(위는 인증 3 + 계정 3). TRD v1.0의 실제 목록을 확인해 맞춘다.

### 3.3 다른 영역이 쓰는 인증 인터페이스 (공동 검토 대상)

- 인증 필터가 요청마다 `memberId`(Long)를 확인해 컨트롤러에 전달한다. 모든 도메인이 같은 방식으로 현재 사용자를 얻도록, **현재 사용자 표현(예: `@AuthenticationPrincipal`로 주입되는 타입)을 한 가지로 정한다.**
- Spring Security 설정(`SecurityFilterChain`)은 `global/config`(재원 영역)에 둘지, `member` 도메인에 둘지 정해야 한다. 제안: 보안 설정 클래스는 `global/config`, 토큰 검증·발급 로직은 `member`. 어느 쪽이든 변경 때마다 상대 리뷰를 받는다.
- 인증 실패는 401, 권한 없음은 403, 타인의 개인 데이터 접근은 404(존재를 숨김).

### 3.4 오류 코드 형식

- 공통 형식은 conventions.md의 `error.code`(`COMMON-0400` 형태). 도메인 코드는 `AUTH-0401`, `MEMBER-0404`처럼 영역 + HTTP 상태.
- 같은 영역·같은 HTTP 상태에서 여러 오류를 구분하는 세부 규칙은 conventions.md가 "첫 도메인 오류 목록 작성 때 정한다"고 했다 → 이 이슈가 첫 사례가 될 수 있으므로 함께 정한다.

---

## 4. 장소 API 계약 후보 (요청/응답 래퍼 기준)

TRD v1.0의 "장소 9개"와 대조해야 한다. 현재 제안:

| 메서드·경로 | 설명 | 비고 |
|---|---|---|
| `GET /places/search?query=` | 장소 이름·주소로 검색한 후보 목록 | 카카오 장소 검색. 동명 장소는 모두 반환 |
| `POST /places/resolve/link` | 공유 텍스트·제목에서 장소명 추출 후 후보 반환 | 서버는 URL을 직접 가져오지 않음(`sharedText`를 앱이 전달) |
| `POST /places/resolve/image` | 이미지에서 장소명 추출 후 후보 반환 | FastAPI 연동(별도 이슈) |
| `POST /saved-places` | 후보를 골라 개인 보관함에 저장 | 요청에 후보 필드(`providerPlaceId`, `name`, `address`, `lat`, `lng`, `category` 등)를 담는다. 형식·범위만 검증. 중복 저장은 `409` 또는 기존 항목 반환(결정 필요) |
| `GET /saved-places` | 개인 보관함 목록(분류 필터) | 페이지네이션 방식은 이 API에서 정한다(conventions.md 미정 항목) |
| `GET /saved-places/{savedPlaceId}` | 상세 | 운영시간은 값이 있을 때만 |
| `DELETE /saved-places/{savedPlaceId}` | 보관함에서 삭제 | 공유된 장소는 공유 정책(재원)과 연동 |
| `GET /saved-places/{savedPlaceId}/nearby` | 주변 놀거리 | 저장된 장소의 좌표로 요청 때 카테고리 검색 |
| `POST /saved-places/{savedPlaceId}/share` | 공동 보관함에 공유 | **재원 영역(`couple`/공유)과 합의 후 별도 이슈** |

성공 응답 예(저장, `201 Created`):

```json
{
  "success": true,
  "data": { "savedPlaceId": 12 },
  "error": null
}
```

---

## 5. 재원에게 확인할 질문

1. 컨벤션(복수형 테이블, `IDENTITY`)과 현재 코드(`course_place`, 기본 `@GeneratedValue`)가 다른데, 어느 쪽으로 맞출 건가요? 새 도메인(`members`, `saved_places`)은 컨벤션을 따라 만들어도 되나요?
2. 장소는 A안(`saved_places` 한 테이블)으로 시작하려고 합니다. 코스가 보관함을 참조하지 않고 스냅샷 컬럼(`provider`, `provider_place_id`, `address`, `lat`, `lng`)을 갖는 방식(§2.3)이 괜찮은가요? 나중에 `places`를 분리할 필요가 있다고 보시는 지점이 있나요?
3. Spring Security 설정을 어디에 둘까요(`global/config` vs `member`)? 누가 작성할까요?
4. 현재 사용자(`memberId`)를 컨트롤러로 전달하는 방식을 한 가지로 정해도 될까요?
5. 이메일 가입을 이번 MVP에서 뺄까요? 소셜은 카카오·구글 둘 다인가요, 카카오 먼저인가요?
6. 탈퇴(`DELETE /members/me`) 때 커플 연결과 공동 데이터는 어떻게 처리하나요?
7. #13(전역 에러 핸들러)에서 도메인 오류 코드(`AUTH-xxxx` 등)를 받는 구조도 같이 정해지나요?

---

## 6. 이 이슈의 완료 기준

- 위 질문에 대한 합의 결과가 PR(문서) 또는 이슈 코멘트로 남는다.
- 합의된 `members`, `saved_places` 테이블 정의가 ERD(DBML, #10 기준)에 반영된다.
- 인증·장소 API 목록이 TRD v1.0과 일치하도록 정리된다.
- 미정으로 남는 항목은 미정으로 표시한다.
