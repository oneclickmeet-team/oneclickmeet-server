# 회원·장소 데이터 모델 및 인증·API 계약

- 작성: 승진 (member · place 담당)
- 상태: **구현 반영(2026-11-09, 코스 장소 스냅샷 11/11 반영).** 맨 앞 "구현 결과"가 현재 기준이고, 그 아래 §0~§6은 10/30 제안 원문(결정 기록)이다. 둘이 다르면 "구현 결과"가 맞다.
- 승진 결정(10/30): 장소는 **A안(`saved_places` 한 테이블)으로 시작**한다. 필요해지면 B안(`places` 분리)으로 옮긴다(§2.2).
- 기준 자료: `main`@3fd3ce9 (#14 머지 후), `docs/conventions.md`, `CODEOWNERS`, 백엔드 TRD 초안 v0.1(연경, 10/14), 기능 명세서 v0.1
- 확인하지 못한 것: TRD v1.0(10/18, 재원 작성)의 API 49개 목록 원문. 아래 API 목록은 TRD v1.0과 대조해야 한다.

---

## 구현 결과 (2026-11-09)

### 데이터 모델

| 테이블 | 실제 구현 | 제안과 다른 점 |
|---|---|---|
| `members` (#68) | `id`(IDENTITY), `name`(닉네임, NOT NULL), `email`(NULL), `provider`(`KAKAO`/`GOOGLE`), `provider_user_id`, `created_at`, `updated_at`. 유니크 (`provider`, `provider_user_id`) | 컬럼 이름이 `auth_provider`가 아니라 `provider`. `password_hash` 없음(이메일 가입 제외). `course`·`couple`·`comment` 코드가 `members(id, name)`을 직접 조회하므로 `name`을 유지했고, 다른 도메인의 통합 테스트가 `id, name`만 넣기 때문에 `provider`·`provider_user_id`는 DB에서 NULL 허용(가입 코드는 항상 채움). 테스트가 정리되면 NOT NULL로 바꾼다 |
| `refresh_tokens` (#68) | `member_id`, `token_hash`(SHA-256, 유니크), `expires_at`, `revoked_at` | 제안에 없던 테이블. 갱신 토큰 원문은 저장하지 않는다 |
| `saved_places` (#60) | `member_id`, `provider`, `provider_place_id`, `name`, `address`, `road_address`, `category`, `category_group_code`, `lat`, `lng`, `place_url`, `created_at`, `updated_at`. 유니크 (`member_id`, `provider`, `provider_place_id`) | `category_group_code`·`place_url` 추가, `tags`(JSON, nullable, 블로그 태그 최대 5개, #89) 추가, `source_url`·`hours`는 미구현 |
| `activities`, `notifications` (#62) | 회원별 활동 기록과 알림(읽음 시각) | 제안 범위 밖이던 영역 |

#### 코스와 보관함 (§2.3)

개인 코스 생성·수정은 앱이 확정한 장소의 `providerPlaceId`, `name`, `address`, `lat`, `lng`를 `CoursePlace`에 저장한다. 서버는 `providerPlaceId`가 있으면 `provider`를 `KAKAO`로 저장한다. 식별자가 없는 스냅샷은 `provider`와 `provider_place_id`가 `NULL`이다. 스냅샷은 보관함과 독립적으로 저장되며 `original_place_id`가 `NULL`이다. `tags`는 보관함 데이터로 관리한다. [코스 생성·수정 요청 예시](app-integration-guide.md#코스-추천-결과-저장)를 참고한다.

보관함 번호로 새 장소를 추가하면 기존과 같이 `CoursePlaceLookup`으로 본인의 보관함 항목을 찾아 `original_place_id`와 `place_name`을 저장한다. 기존 코스는 두 값을 그대로 사용하며, 수정할 때 유지하는 장소는 `coursePlaceId`로 찾는다. 기존 행의 스냅샷 컬럼에는 `NULL`을 유지한다.

기존 스키마를 유지하는 MySQL 환경에서는 배포 전에 `SHOW COLUMNS FROM course_place;`로 아래 컬럼을 확인한다. 전부 없을 때 아래 DDL을 적용하고, 이미 존재하는 컬럼이 있다면 정의를 확인한 뒤 빠진 컬럼만 추가한다.

```sql
ALTER TABLE course_place
  ADD COLUMN provider VARCHAR(16) NULL,
  ADD COLUMN provider_place_id VARCHAR(64) NULL,
  ADD COLUMN address VARCHAR(300) NULL,
  ADD COLUMN lat DOUBLE NULL,
  ADD COLUMN lng DOUBLE NULL;
```

### 인증 (#68, 재원 승인 후 병합)

- 소셜 로그인(카카오·구글)만 구현했다. 이메일 가입·로그인은 처음에는 제외했으나 **시연에 포함하기로 해 재원이 추가한다**(2026-11-09, §3.1, §5-5).
- 앱이 받은 증표를 서버가 제공자에게 확인한다: 카카오는 `access_token_info`로 **우리 앱(app_id)에 발급된 토큰인지** 확인한 뒤 `v2/user/me`, 구글은 `tokeninfo`의 `aud`·`iss`. 앱 번호(`KAKAO_LOGIN_APP_ID`)·클라이언트 ID(`GOOGLE_LOGIN_CLIENT_ID`)가 없으면 로그인이 동작하지 않는다.
- 토큰: 접근 토큰 JWT(HS256, 30분), 갱신 토큰(14일, 한 번 쓰면 폐기·재발급, 이미 쓴 토큰이 다시 오면 그 회원의 갱신 토큰을 모두 폐기). 비밀키는 `JWT_SECRET`(32바이트 이상). 유효 시간은 제안값이며 정책 확정 시 조정한다.
- 현재 사용자 표현(§3.3, §5-4): **`@AuthenticationPrincipal Long memberId`** 한 가지.
- 보안 설정 위치(§3.3, §5-3): `SecurityFilterChain`은 `global/config`, JWT 발급·검증은 `global/security`, 로그인·회원 로직은 `domain/member`.
- 공개 경로: `POST /api/v1/auth/login/*`, `POST /api/v1/auth/refresh`, `GET /actuator/health`. 나머지는 로그인 필요, 실패 시 JSON 401(`COMMON-0401`).

### API (모든 경로가 `/api/v1` 아래)

| API | 상태 |
|---|---|
| `POST /auth/login/kakao`, `/auth/login/google`, `/auth/refresh`, `/auth/logout` | 구현(#68) |
| `GET` · `PATCH /members/me` | 구현(#68). 수정은 닉네임만 |
| `DELETE /members/me` | 개발하지 않음(시연에 쓰이지 않아 제외, 2026-11-08, §5-6) |
| `GET /places/search` | 구현(#22, #58). 커서 방식으로 변경(#85): `cursor`·`size`, 응답 `items`·`nextCursor`·`hasNext` |
| `POST /places/resolve/link`, `/places/resolve/image` | 구현(#64). AI 서버 내부 API v2.1에 맞춰 연동(#76), 이미지 인식 읽기 제한 70초(#82), 추가 캡처 `extraImages` 최대 4장(#87). 실제 AI 서버 호출 검증은 아직 |
| `POST` · `GET` · `GET /{id}` · `DELETE /saved-places` | 구현(#60). 같은 장소 다시 저장은 409가 아니라 **기존 항목과 200**(새로 저장은 201). 목록은 커서 방식(`items`, `nextCursor`, `hasNext`) |
| `GET /saved-places/{id}/nearby` | 구현(#72) |
| `POST /saved-places/{id}/share` | 미구현(공유 영역과 합의 후) |
| `POST /recommendations/courses` | 구현(#66). AI 서버(R01)에 위임하도록 변경(#80): 방문 순서별 카테고리(`categorySequence`)·필수 장소 위치(`requiredPlaceOrder`) 입력, AI 서버를 쓸 수 없으면 502. 결과는 저장하지 않음 |
| `GET /activities`, `GET /notifications`, `PATCH /notifications/{id}/read` | 구현(#62) |
| `POST /devices`, `POST /devices/unregister` | 구현(#74). 푸시용 기기 토큰 등록·해제. 실제 FCM 전송은 후속 |

### 오류 코드 (§3.4, §5-7)

- #13(#25로 병합)의 `ErrorCode` 구조를 따른다: 영역 + HTTP 상태. 같은 영역·같은 상태가 여럿이면 끝자리를 바꾼다(예: `MEMBER-0401` 소셜 증표 오류, `MEMBER-0402` 갱신 토큰 오류).
- 사용 중인 도메인 코드: `PLACE-0404`, `PLACE-0502`, `MEMBER-0401`, `MEMBER-0402`, `MEMBER-0404`, `MEMBER-0502`, `NOTIFICATION-0404`, `RECOMMENDATION-0400`, `RECOMMENDATION-0401`, `RECOMMENDATION-0502`(AI 추천 서버 실패).

### §5 질문의 현재 상태

| 질문 | 상태 |
|---|---|
| 1. 테이블 이름·PK 전략 | 새 테이블은 컨벤션대로 복수형 snake_case + `IDENTITY` |
| 2. 코스의 장소 참조 방식 | 개인 코스가 장소 스냅샷을 보관하고, 이전 보관함 번호 기반 코스도 조회·수정할 수 있다. 위 "코스와 보관함" 참고 |
| 3. Security 설정 위치 | `global/config`(재원 승인) |
| 4. 현재 사용자 표현 | `@AuthenticationPrincipal Long memberId` |
| 5. 이메일 가입 | 카카오·구글 구현. 이메일 가입·로그인은 시연에 포함하기로 해 재원이 추가 예정 |
| 6. 탈퇴 처리 | **개발하지 않음**(시연에 쓰이지 않음, 2026-11-08) |
| 7. 도메인 오류 코드 | #25의 `ErrorCode` 구조로 해결 |

### 아직 남은 것

- 공동 보관함 공유, 메모의 보관함 장소 접근 확인(`MemoTargetAccess`의 `TODO(#19)`, 재원 영역): 개인 장소(`PLACE`)와 공동 장소(`SHARED_PLACE`) 메모를 모두 연결한다. 공동 장소는 공동 보관함 대상 ID·관계별 접근 권한·공유 취소/삭제 시 메모 처리 규칙을 먼저 맞춘다.
- 공유·삭제가 일어날 때 활동 기록·알림을 남기는 연결(`course`에서 `ActivityRecorder` 호출, 재원 영역): 시연 전에 코스 공유·공유 취소·삭제를 연결한다. 코스 변경이 성공한 경우에만 기록한다.
- 이메일 회원가입·로그인(재원, 시연 전).
- 업로드 한도 설정(파일당 5MB, 요청 전체 6MB, 재원 영역), 토큰 유효 시간 정책 확정. `.env.example`의 환경변수 항목은 #84에서 추가했다.
- 실제 AI 서버 호출 검증(시연용 주소·키는 11/11 전 수령 예정), FCM 실제 전송(후속).
- 보관함에 AI 카테고리 코드(`categoryCode`)를 저장할지는 보류했다. 지금은 카카오 `categoryGroupCode`를 추천 요청 때 AI 코드로 변환하고, 매핑이 안 되는 값은 AI가 추론한다. 저장하려면 인식·직접 검색에서 값을 어떻게 채울지와 기존 보관함의 빈 값 처리를 먼저 맞춘 뒤 스키마 변경을 검토한다.
- API 목록을 TRD v1.0과 대조, ERD(DBML, #10)에 실제 테이블 반영.

---

## 0. 먼저 알아둘 현재 상태 (10/30 제안 당시)

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
