# 앱 팀 연동 가이드와 협의 필요 항목

- 대상: 앱(안드로이드·iOS) 팀
- 작성: 승진 (member · place · recommendation · activity · notification 담당) — 2026-11-05 기준
- 범위: 승진 담당 영역의 API. 코스·커플·메모·체크(재원 담당) API는 재원의 문서를 볼 것.
- **상태 표기:** 표의 "구현"은 `main`에 병합된 것, "제안"은 코드는 있지만 앱 팀과 계약을 확정하지 않은 것, "미구현"은 서버에 없는 것이다.
- **확인하지 못한 것:** 실제 앱과 서버를 붙여 본 적이 없다. 아래 요청·응답 예시는 서버 코드와 테스트 기준이고, 카카오·구글·AI 서버와의 실제 호출은 가짜 응답으로만 확인했다.

---

## 1. 공통 규칙

| 항목 | 규칙 |
|---|---|
| 주소 | 모든 API는 `/api/v1/...`. **배포된 서버는 아직 없다**(로컬에서만 실행). 개발 서버 주소·HTTPS는 §4-10 |
| 인증 | 로그인·토큰 갱신·`GET /actuator/health`를 빼고 모두 `Authorization: Bearer {accessToken}` 필수 |
| 응답 형식 | `{ "success": true, "data": ..., "error": null }` / 실패 `{ "success": false, "data": null, "error": { "code": "...", "message": "...", "errors": [ { "field": "...", "message": "..." } ] } }`. `errors`는 입력 검증(400)일 때만 있다 |
| 시각 | 모두 UTC ISO-8601(예 `2026-11-05T03:00:00Z`). 한국 시간 변환은 앱이 한다 |
| 목록 | 커서 방식: 요청 `cursor`(이전 응답의 `nextCursor` 그대로), `size`(기본 20, 1~100). 응답 `items`, `nextCursor`, `hasNext`. 커서는 불투명 문자열이므로 해석하지 말 것. 다른 회원·다른 필터의 커서는 400 |
| 남의 데이터 | 남의 개인 데이터를 요청하면 403이 아니라 **404**(존재를 숨김). 403은 쓰지 않는다 |

### 오류 코드별 앱 동작 제안

| 코드 | HTTP | 의미 | 앱이 할 일 |
|---|---|---|---|
| `COMMON-0400` | 400 | 입력값 오류 | `error.errors[]`의 `field`로 화면에 표시 |
| `COMMON-0401` | 401 | 접근 토큰 없음·만료·위조 | **갱신 후 원래 요청 재시도(1회)**. 갱신도 실패하면 로그인 화면 |
| `MEMBER-0401` | 401 | 소셜 로그인 증표가 올바르지 않음 | 소셜 로그인을 처음부터 다시 |
| `MEMBER-0402` | 401 | 갱신 토큰이 만료·폐기·모름 | 저장한 토큰을 지우고 로그인 화면 |
| `MEMBER-0404` | 404 | 회원 없음 | 로그아웃 처리 |
| `MEMBER-0502` | 502 | 소셜 로그인 제공자를 잠시 쓸 수 없음 | "잠시 후 다시" 안내 |
| `PLACE-0404` | 404 | 보관함 장소 없음(남의 장소 포함) | 목록을 새로 고침 |
| `PLACE-0502` | 502 | 카카오 장소 검색 실패 | "잠시 후 다시" 안내 |
| `RECOMMENDATION-0400` | 400 | 기준 장소와 지역을 둘 다 보냈거나 둘 다 안 보냄 | 입력 흐름 점검 |
| `RECOMMENDATION-0401` | 400 | 지원하지 않는 지역 | 버튼으로 판단할 수 있는 6곳만 보여 주기 |
| `NOTIFICATION-0404` | 404 | 알림 없음 | 목록 새로 고침 |
| `COMMON-0413` | 413 | 업로드 크기 초과(이미지) | 압축 후 재시도 |
| `COMMON-0500` | 500 | 서버 오류 | 재시도 안내 |

> `MEMBER-0401`(소셜 증표 오류)와 `COMMON-0401`(서버 토큰 만료)은 다르다. 앞의 것은 로그인 API에서만, 뒤의 것은 로그인 이후 API에서 나온다.

---

## 2. 로그인 흐름

```text
앱 ─(1) 카카오/구글 SDK로 로그인 ─▶ 카카오 액세스 토큰 / 구글 ID 토큰
앱 ─(2) POST /api/v1/auth/login/kakao | google ─▶ 서버
서버 ─(3) 제공자에 토큰 확인 ─▶ 카카오 / 구글
서버 ─(4) 회원 찾기·가입 후 서버 토큰 발급 ─▶ 앱
앱 ─(5) 이후 API는 서버 토큰(Authorization: Bearer)로 호출
```

- 앱이 서버로 보내는 것은 **카카오 액세스 토큰**, **구글 ID 토큰**이다(인가 코드나 이메일이 아님).
- 서버는 카카오 토큰이 **우리 카카오 앱에 발급된 것인지** 확인한다. 다른 카카오 앱의 토큰이면 `MEMBER-0401`이다. 구글은 ID 토큰의 대상(`aud`)이 서버에 설정한 클라이언트 ID와 같아야 한다.

| API | 상태 | 요청 | 성공 응답 `data` |
|---|---|---|---|
| `POST /auth/login/kakao` | 구현 | `{ "accessToken": "..." }` | `{ tokens: { accessToken, refreshToken, tokenType: "Bearer", expiresIn }, newMember, member: { memberId, provider, nickname, email, createdAt } }` |
| `POST /auth/login/google` | 구현 | `{ "idToken": "..." }` | 위와 같음 |
| `POST /auth/refresh` | 구현 | `{ "refreshToken": "..." }` | `{ accessToken, refreshToken, tokenType, expiresIn }` |
| `POST /auth/logout` | 구현 | `{ "refreshToken": "..." }` + Bearer | 204(본문 없음) |
| `GET /members/me` | 구현 | — | `{ memberId, provider, nickname, email, createdAt }` (`email`은 null일 수 있음) |
| `PATCH /members/me` | 구현 | `{ "nickname": "..." }`(1~30자) | 갱신된 회원 정보 |
| `DELETE /members/me` | **미구현** | — | 탈퇴 정책 미정(§4-9) |

- `expiresIn`은 **접근 토큰이 유효한 시간(초)**. 제안값은 접근 30분, 갱신 14일(정책 확정 시 변경 가능).
- `newMember`가 true면 이번 로그인으로 처음 가입한 것이다(환영 화면 등에 쓸 수 있음).
- 닉네임: 카카오·구글 프로필에서 받아 저장한다. 없으면 "사용자", 30자를 넘으면 잘라 저장한다.

### 토큰 처리 규칙 (중요)

1. **갱신 토큰은 한 번만 쓴다.** `/auth/refresh`를 부르면 **새 갱신 토큰이 왔으므로 반드시 저장을 교체**해야 한다. 이전 것은 쓸 수 없다.
2. **같은 갱신 토큰으로 동시에 여러 번 호출하지 말 것.** 이미 쓴 갱신 토큰이 다시 오면 서버는 탈취로 보고 **그 회원의 모든 기기 로그인을 폐기**한다. 화면 여러 곳에서 동시에 401을 받아도 갱신은 한 번만 하고 나머지 요청은 기다렸다가 새 토큰으로 재시도해야 한다.
3. 토큰은 Keychain(iOS)·Keystore기반 암호화 저장소(Android)에 보관하고 로그에 출력하지 말 것.
4. 로그아웃 때 `/auth/logout`을 부르고 기기의 토큰을 지운다(서버 호출이 실패해도 기기에서는 지운다).

---

## 3. 장소·보관함·추천·알림 API

### 장소 검색·인식

| API | 상태 | 요청 | 응답 `data` / 주의 |
|---|---|---|---|
| `GET /places/search` | 구현 | `query`(1~100자), `page`(기본 1), `size`(1~15, 기본 10) | `{ candidates: [{ providerPlaceId, name, address, roadAddress, category, categoryGroupCode, lat, lng, placeUrl }], page, hasNext }`. 위치 파라미터는 없다(보내도 무시). 결과는 검색어당 **최대 45건**, 그 뒤 쪽은 빈 목록 |
| `POST /places/resolve/link` | 구현 | `{ "sharedText": "..." }`(최대 2000자). 공유받은 텍스트 전체를 그대로. **서버는 URL을 열지 않는다** | `{ status: "CANDIDATES_FOUND" \| "NEEDS_SEARCH", candidates: [...] }`. `NEEDS_SEARCH`면 직접 검색 화면으로 안내 |
| `POST /places/resolve/image` | 구현 | multipart, 파트 이름 `image`. JPEG·PNG·WebP, 서버 검증 상한 5MB | 위와 같음. 업로드 한도는 §4-5 |

- `providerPlaceId`는 카카오 장소 ID(문자열). 동명 장소는 걸러내지 않는다.

### 보관함 (`/saved-places`)

| API | 상태 | 요청 | 응답 |
|---|---|---|---|
| `POST /saved-places` | 구현 | 검색 후보의 필드를 그대로: `providerPlaceId`, `name`, `address`, `roadAddress`, `category`, `categoryGroupCode`, `lat`, `lng`, `placeUrl`(`providerPlaceId`·`name`·`address`·`lat`·`lng` 필수) | **새로 저장 201, 이미 저장한 장소는 200**(오류가 아님, 같은 항목을 돌려줌). `{ savedPlaceId, providerPlaceId, name, address, roadAddress, category, categoryGroupCode, lat, lng, placeUrl, savedAt }` |
| `GET /saved-places` | 구현 | `cursor`, `size`, `categoryGroupCode`(선택, 예 `CE7`) | `{ items: [...], nextCursor, hasNext }`, 최신 저장순 |
| `GET /saved-places/{id}` | 구현 | — | 위 항목 1건 |
| `DELETE /saved-places/{id}` | 구현 | — | 204 |
| `GET /saved-places/{id}/nearby` | 구현 | `category`=`CAFE`\|`RESTAURANT`\|`EXHIBITION`\|`PARK`(기본 `CAFE`) | `{ savedPlaceId, category, items: [{ place: {후보 필드}, distanceMeters }] }`. 저장된 장소 기준 반경 1km, 가까운 순 |
| `POST /saved-places/{id}/share` | **미구현** | — | 공동 보관함 공유(커플 영역과 합의 후) |

### 코스 추천

| API | 상태 | 요청 | 응답 `data` |
|---|---|---|---|
| `POST /recommendations/courses` | 구현 | `{ "anchorSavedPlaceId": 5 }` **또는** `{ "area": "혜화" }` 중 **하나만**. 지원 지역: 잠실, 성신여대입구역, 한성대입구역, 혜화, 서촌, 연남 | `{ status: "OK" \| "NOT_ENOUGH_CANDIDATES", courses: [{ candidateKey, totalWalkMinutes, places: [{ order, providerPlaceId, name, address, roadAddress, category, categoryGroupCode, lat, lng, placeUrl, savedPlaceId, walkMinutesFromPrevious }] }] }` |

- 최대 3개 코스, 각 3~5곳, 이웃 장소 사이 도보 10분 이내(직선거리 기반 **어림값**). `savedPlaceId`는 내 보관함에 있는 장소일 때만 값이 있다.
- `NOT_ENOUGH_CANDIDATES`(후보 부족)는 오류가 아니라 200이며, 서버가 지역을 자동으로 넓히지 않는다.
- **추천 결과는 서버에 저장되지 않는다.** `candidateKey`로 코스를 저장하는 API는 없다(§4-7).
- 분위기 정보로 거르는 기능은 아직 없다(외부 요약 연동 정책 미정).

### 활동 기록·알림·푸시

| API | 상태 | 요청 | 응답 `data` |
|---|---|---|---|
| `GET /activities` | 구현 | `cursor`, `size` | `{ items: [{ activityId, type, targetType, targetId, summary, createdAt }], nextCursor, hasNext }`. `type`: `COURSE_SHARED`, `COURSE_SHARE_CANCELED`, `COURSE_DELETED`, `AMOUNT_CHANGED` |
| `GET /notifications` | 구현 | `cursor`, `size` | `{ items: [{ notificationId, type, title, body, targetType, targetId, read, createdAt }], unreadCount, nextCursor, hasNext }` |
| `PATCH /notifications/{id}/read` | 구현 | — | 읽음 처리된 알림 1건(여러 번 호출해도 안전) |
| `POST /devices` | 제안(#74) | `{ "platform": "ANDROID"\|"IOS", "token": "FCM 토큰" }` | 204. 등록은 멱등(같은 토큰 재등록 안전) |
| `POST /devices/unregister` | 제안(#74) | `{ "token": "..." }` | 204 |

- **아직 연결되지 않았다:** 서버의 공유·삭제 동작이 활동 기록·알림을 남기는 코드는 서비스가 제공되어 있지만, 코스·커플 쪽(재원)에서 호출하는 연결이 없다. 따라서 지금은 실제로 생기는 알림이 없을 수 있다.
- **FCM 푸시 전송은 미구현**이다(키·의존성 필요). 알림 목록은 조회(polling)로 보여 주는 것이 현재 유일한 방법이다.

---

## 4. 협의가 필요한 항목

표의 "제안"은 서버 승진이 정해 둔 기본값이다. 앱 팀과 맞지 않으면 서버를 바꾸는 여지가 있다.

### 로그인

| # | 안건 | 왜 정해야 하나 | 제안 | 함께 정할 사람 |
|---|---|---|---|---|
| 1 | **소셜 로그인 증표 종류** | 서버는 카카오 액세스 토큰, 구글 ID 토큰만 받는다 | 변경 없이 이 방식 | 앱 담당 |
| 2 | **구글 클라이언트 ID** | ID 토큰의 `aud`가 서버 설정과 같아야 한다. 서버는 현재 **하나만** 허용. 안드로이드·iOS가 서로 다른 클라이언트 ID를 쓰면 일부가 실패한다 | 앱이 ID 토큰을 받을 때 지정하는 서버용(웹) 클라이언트 ID 하나를 공유. 여러 개가 필요하면 서버가 목록을 받도록 바꾼다 | 앱·서버 |
| 3 | **카카오 앱 일치** | 서버는 토큰이 우리 카카오 앱(앱 ID `1629479`)에서 발급된 것인지 확인한다. 앱에서 쓰는 네이티브 앱 키가 다른 카카오 앱의 것이면 모든 로그인이 거절된다 | 앱의 카카오 SDK 키를 승진이 만든 카카오 앱(`1629479`)의 네이티브 앱 키로 맞추고, 플랫폼(안드로이드 패키지명·키 해시, iOS 번들 ID)을 카카오 콘솔에 등록 | 앱 담당 + 승진 |
| 4 | **카카오 동의항목** | 닉네임은 필수 동의로 설정 완료. 이메일은 비즈 앱 전환이 필요할 수 있어 아직 안 받는다 | 이메일은 쓰지 않는 것으로 한다(`email`은 null일 수 있다고 가정하고 개발) | 앱 담당 |
| 5 | **iOS: Apple 로그인 요구** | App Store 심사 정책상 다른 서비스의 소셜 로그인을 제공하면 Apple 로그인 또는 정책이 허용하는 동등한 옵션을 요구할 수 있다. **정책을 정확히 확인하지 못했다** | iOS 출시 계획이 있으면 앱 팀이 심사 기준을 확인하고, 필요하면 서버에 Apple 로그인(신규 개발)을 추가 | 앱 담당 → 승진 |

### 토큰·세션

| # | 안건 | 제안 | 함께 정할 사람 |
|---|---|---|---|
| 6 | 토큰 유효 시간 | 접근 30분, 갱신 14일. 로그인이 자주 풀리면 갱신을 늘린다 | 승진·재원·앱 |
| 6-1 | 갱신 요청 동시성 처리 | §2 토큰 처리 규칙 2번을 앱이 지킨다(한 번에 한 개의 갱신 요청) | 앱 담당 |

### 업로드·호출

| # | 안건 | 왜 정해야 하나 | 제안 | 함께 정할 사람 |
|---|---|---|---|---|
| 7 | **이미지 업로드 용량** | 서버 검증 상한은 5MB지만, **Spring 기본 업로드 한도가 파일당 1MB**라 설정을 바꾸기 전에는 1MB를 넘으면 413이다. 폰 스크린샷은 보통 1MB를 넘는다 | 서버 환경변수 `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=5MB`, `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=6MB`를 설정(재원 영역). 그전까지 앱이 이미지를 긴 변 1280px·JPEG 품질 80 정도로 줄여 1MB 이하로 보낸다 | 재원·앱 |
| 8 | **장소 검색 호출 빈도** | 카카오 호출 일일 한도를 확인하지 못했다. 한 사용자가 글자를 칠 때마다 호출하면 한도에 걸릴 수 있다 | 자동완성 대신 **검색 버튼·엔터로 호출**하거나 입력 후 500ms이상 멈추면 호출(디바운스) | 앱 담당 |

### 탈퇴·계정

| # | 안건 | 왜 정해야 하나 | 제안 | 함께 정할 사람 |
|---|---|---|---|---|
| 9 | **회원 탈퇴** | 서버에 `DELETE /members/me`가 없다. 탈퇴 시 커플 연결이나 공동 데이터를 어떻게 할지 정책이 없다. 앱 스토어는 계정을 만들 수 있으면 **앱 안에서 계정 삭제**를 요구할 수 있다(정책 확인 필요) | 출시 전에 탈퇴 정책을 정하고 서버에 구현. 정해지기 전까지 탈퇴 버튼은 숨긴다 | 승진·재원·앱 |

### 코스·추천

| # | 안건 | 왜 정해야 하나 | 제안 | 함께 정할 사람 |
|---|---|---|---|---|
| 10 | **추천받은 코스 저장 방법** | 추천 결과는 저장되지 않고, `candidateKey`로 저장하는 API는 없다. 코스 저장 API(재원)는 장소를 **보관함 장소 번호**로 참조하는 것으로 보인다(정확한 필드는 재원 문서로 확인 필요) | 임시 방안: 앱이 추천 코스의 장소를 하나씩 `POST /saved-places`로 보관함에 저장(이미 있으면 200으로 같은 항목)하고, 얻은 `savedPlaceId`들로 코스 저장 API를 호출. 장기적으로는 서버가 `candidateKey`를 받아 한 번에 저장하도록 재원과 계약을 만든다 | 재원·승진·앱 |
| 11 | 지원 지역 6곳의 화면 표기 | 서버는 화면 이름(`잠실` 등) 또는 영문 이름(`JAMSIL`)을 모두 받는다. 지역 목록을 내려주는 API는 없다 | 앱이 6곳을 하드코딩(변경 시 앱 업데이트가 필요하다면 지역 목록 API를 추가) | 앱 담당 |

### 알림·푸시

| # | 안건 | 제안 | 함께 정할 사람 |
|---|---|---|---|
| 12 | **기기 토큰 등록 API 모양** | `POST /devices {platform, token}`, 해제는 `POST /devices/unregister {token}`(#74). 앱 실행·로그인 때와 토큰이 바뀔 때 등록, 로그아웃 때 해제. 다른 모양을 원하면 알려 달라 | 앱 담당 |
| 13 | **FCM 전송 시점** | 서버에 FCM 연동이 없다(Firebase 서비스 계정 키·의존성 필요). 그전까지 앱은 알림 목록 조회로 구현 | 재원·승진 |
| 14 | 푸시 페이로드 | 서버가 보낼 값: `title`, `body` + `data`(`type`, `targetType`, `targetId`). 앱이 탭 시 이동할 화면을 `targetType`로 구분 | 앱 담당 |

### 인프라

| # | 안건 | 제안 | 함께 정할 사람 |
|---|---|---|---|
| 15 | **개발 서버 주소·HTTPS** | 배포된 서버가 없다. 알맞은 안드로이드는 평문(HTTP) 요청이 기본으로 막히고 iOS도 유사하다. 실기기로 테스트하려면 내 PC의 주소나 터널이 필요하다 | 개발 중에는 개인 PC에서 서버를 띄우고 에뮬레이터(안드로이드 에뮬레이터는 `10.0.2.2:8080`)로 연결. 배포 서버와 HTTPS는 비용·일정과 함께 팀이 결정 | 재원·팀 전체 |
| 16 | **시간대** | 서버는 모두 UTC로 준다 | 앱이 한국 시간(KST)으로 변환해 표시 | 앱 담당 |

---

## 5. 아직 없는 것과 알고 있는 한계

- **실제 연동 검증이 없다.** 카카오 로그인은 실제 앱에서 받은 토큰으로 아직 시험하지 못했다. 틀린 토큰이 `MEMBER-0401`로 거절되는 것만 확인했다. 앱 팀과 함께 처음 로그인이 되는지 보는 시점이 필요하다.
- AI 서버(링크·이미지 인식)는 연결되어 있지 않다. 링크는 공유 텍스트에서 줄을 단순히 잘라 내는 규칙으로 대체하고, 이미지는 항상 `NEEDS_SEARCH`가 나올 수 있다. 앱은 두 경우에 항상 직접 검색으로 이어지는 화면이 필요하다.
- 재원 담당 API(코스·커플·메모·첨언)는 이 문서 범위 밖이다.
- 로그인 시도 횟수 제한, 기기별 로그아웃 관리, 계정 삭제는 없다.
- 서버 오류 메시지(`error.message`)는 한국어 안내 문구이다. 화면에 그대로 써도 되지만, 문구는 바뀔 수 있으므로 동작은 `error.code`로 나눠야 한다.

## 6. 앱 팀이 처음 연동할 때 순서 (제안)

1. 서버를 개인 PC에서 띄운다(`GET /actuator/health`가 200이면 준비).
2. 카카오 로그인 → `POST /auth/login/kakao` → 토큰 저장 → `GET /members/me`.
3. `GET /places/search` → `POST /saved-places` → `GET /saved-places`.
4. 토큰 만료 시나리오(접근 토큰을 일부러 짧게 하거나 지워 보기): 401 → 갱신 → 재시도.
5. 추천·알림·이미지 등은 위가 되면 이어서.
