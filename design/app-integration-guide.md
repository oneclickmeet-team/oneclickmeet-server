# 앱 팀 연동 가이드와 협의 필요 항목

- 대상: 앱(안드로이드·iOS) 팀
- 작성: 승진 (member · place · recommendation · activity · notification 담당) — 2026-11-09 기준
- 범위: 승진 담당 영역의 API와 [추천 결과를 개인 코스에 담는 방법](#코스-추천-결과-저장). 나머지 코스·커플·메모·체크 API는 재원의 문서를 볼 것.
- **상태 표기:** 표의 "구현"은 `main`에 병합된 것, "제안"은 코드는 있지만 앱 팀과 계약을 확정하지 않은 것, "미구현"은 서버에 없는 것이다.
- **확인하지 못한 것:** 실제 앱과 서버를 붙여 본 적이 없다. 아래 요청·응답 예시는 서버 코드와 테스트 기준이고, 카카오·구글·AI 서버와의 실제 호출은 가짜 응답으로만 확인했다. AI 서버는 시연용 주소·키를 11/11 전에 받을 예정이라 그 뒤에 실제 호출을 확인한다.

---

## 1. 공통 규칙

| 항목 | 규칙 |
|---|---|
| 주소 | 모든 API는 `/api/v1/...`. **배포된 서버는 아직 없다**(로컬에서만 실행). 개발 서버 주소·HTTPS는 §4-10 |
| 인증 | 로그인·토큰 갱신·`GET /actuator/health`를 빼고 모두 `Authorization: Bearer {accessToken}` 필수 |
| 응답 형식 | `{ "success": true, "data": ..., "error": null }` / 실패 `{ "success": false, "data": null, "error": { "code": "...", "message": "...", "errors": [ { "field": "...", "message": "..." } ] } }`. `errors`는 입력 검증(400)일 때만 있다 |
| 시각 | 모두 UTC ISO-8601(예 `2026-11-05T03:00:00Z`). 한국 시간 변환은 앱이 한다 |
| 목록 | 커서 방식: 요청 `cursor`(이전 응답의 `nextCursor` 그대로), `size`(기본 20, 1~100). 응답 `items`, `nextCursor`, `hasNext`. 커서는 불투명 문자열이므로 해석하지 말 것. 다른 회원·다른 필터의 커서는 400. 예외: `GET /places/search`는 `size` 기본 10, 1~15 |
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
| `RECOMMENDATION-0502` | 502 | AI 추천 서버를 쓸 수 없음(다른 방식으로 대신하지 않는다) | "잠시 후 다시" 안내 |
| `NOTIFICATION-0404` | 404 | 알림 없음 | 목록 새로 고침 |
| `COMMON-0500` | 500 | 서버 오류 | 재시도 안내 |

> 이미지 인식(`POST /places/resolve/image`)은 장당 5MB 초과·허용하지 않는 형식(JPEG·PNG·WebP 외)·내용이 형식과 다른 파일이면 400(`COMMON-0400`)이다. 파일이 5MB를 넘으면 서버 설정(파일당 5MB, 요청 전체 30MB) 단계에서 먼저 HTTP 413(`COMMON-0413`, 메시지 "요청을 처리할 수 없습니다.")으로 거절된다(2026-11-15 확인). 앱은 413이면 "사진 크기가 너무 큽니다"처럼 안내하면 된다.

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
| `DELETE /members/me` | **개발하지 않음** | — | 시연에 쓰이지 않아 제외(2026-11-08 결정, §4-9) |
| 이메일 회원가입·로그인 | **예정** | — | 시연에 포함하기로 했고 재원이 추가한다. 요청·응답은 확정되면 이 문서에 반영한다 |

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
| `GET /places/search` | 구현 | `query`(1~100자), `cursor`(첫 쪽은 생략, 이후는 이전 응답의 `nextCursor`), `size`(1~15, 기본 10) | `{ items: [{ providerPlaceId, name, address, roadAddress, category, categoryGroupCode, lat, lng, placeUrl }], nextCursor, hasNext }`. 위치 파라미터는 없다(보내도 무시). 결과는 검색어당 **최대 45건**이고 마지막 쪽은 `hasNext`가 false, `nextCursor`가 null. 검색어나 `size`를 바꾸면 이전 커서는 쓸 수 없다(400) |
| `POST /places/resolve/link` | 구현 | `{ "sharedText": "..." }`(최대 2000자). 공유받은 텍스트 전체를 그대로. **서버는 URL을 열지 않는다** | `{ status: "CANDIDATES_FOUND" \| "NEEDS_SEARCH", extractedQuery, candidates: [...] }`. `NEEDS_SEARCH`면 직접 검색 화면으로 안내하고 `extractedQuery`가 있으면 검색창에 채운다. `candidates`의 항목은 검색 후보 필드에 `matchType`(`EXACT`면 기본 선택, `SIMILAR`면 접어서 `matchReasons`와 함께 표시), `matchReasons`, `tags`(`{label, mentionCount}`, 근거가 약하면 빈 목록)가 더해진다 |
| `POST /places/resolve/image` | 구현 | multipart, 원본 한 장은 파트 이름 `image`, 추가 캡처는 파트 이름 `extraImages`로 최대 4장(없어도 됨, **같은 장소**의 다른 화면(홈·메뉴·정보 탭)용이며 장소가 서로 다른 사진을 한 번에 보내는 용도가 아니다. 장수만큼 인식 시간이 늘어난다). JPEG·PNG·WebP, 장당 서버 검증 상한 5MB. 추가 캡처가 5장 이상이거나 한 장이라도 올바르지 않으면 400(`COMMON-0400`) | 위와 같음. 업로드 한도는 §4-7. **최대 1분 이상 걸릴 수 있으므로 요청 대기 시간을 70초 이상으로** 둔다(AI 서버의 보정 읽기가 켜지면 최대 약 60초) |

- `providerPlaceId`는 카카오 장소 ID(문자열). 동명 장소는 걸러내지 않는다.
- 링크·이미지 인식은 AI 서버가 장소 이름 추출부터 카카오 매칭까지 처리한다(서버 연동 완료. 실제 AI 서버 호출은 11/11에 링크·이미지·코스 추천 모두 확인했다). AI 서버를 쓸 수 없으면 링크는 공유 텍스트에서 규칙으로 이름을 뽑아 카카오에서 검색하고, 이미지는 `NEEDS_SEARCH`로 응답한다.
- 이미지 인식은 보정 읽기를 켜면 캡처 원본이 외부 AI(OpenAI)로 전송된다. 사용자 안내가 필요하면 앱에서 문구를 정한다.

### 보관함 (`/saved-places`)

| API | 상태 | 요청 | 응답 |
|---|---|---|---|
| `POST /saved-places` | 구현 | 검색 후보의 필드를 그대로: `providerPlaceId`, `name`, `address`, `roadAddress`, `category`, `categoryGroupCode`, `lat`, `lng`, `placeUrl`(`providerPlaceId`·`name`·`address`·`lat`·`lng` 필수), 선택 `tags`(`[{label, mentionCount}]`, 최대 5개). 링크·이미지 인식 결과의 `tags`를 그대로 보내며, 없거나 비어 있어도 저장된다 | **새로 저장 201, 이미 저장한 장소는 200**(오류가 아님, 같은 항목을 돌려줌). `{ savedPlaceId, providerPlaceId, name, address, roadAddress, category, categoryGroupCode, lat, lng, placeUrl, savedAt, tags }`(`tags`는 없으면 `[]`). 목록·상세 조회 응답에도 `tags`가 같이 내려간다. 이미 저장한 장소를 다시 저장해도 처음 저장한 값(태그 포함)은 바뀌지 않는다 |
| `GET /saved-places` | 구현 | `cursor`, `size`, `categoryGroupCode`(선택, 예 `CE7`) | `{ items: [...], nextCursor, hasNext }`, 최신 저장순 |
| `GET /saved-places/{id}` | 구현 | — | 위 항목 1건 |
| `DELETE /saved-places/{id}` | 구현 | — | 204 |
| `GET /saved-places/{id}/nearby` | 구현 | `category`=`CAFE`\|`RESTAURANT`\|`EXHIBITION`\|`PARK`(기본 `CAFE`) | `{ savedPlaceId, category, items: [{ place: {후보 필드}, distanceMeters }] }`. 저장된 장소 기준 반경 1km, 가까운 순 |
| `POST /saved-places/{id}/share` | **미구현** | — | 공동 보관함 공유(커플 영역과 합의 후) |

### 코스 추천

| API | 상태 | 요청 | 응답 `data` |
|---|---|---|---|
| `POST /recommendations/courses` | 구현 | 아래 요청 참고 | `{ status: "OK" \| "NOT_ENOUGH_CANDIDATES", reason, courses: [{ candidateKey, title, totalWalkMinutes, places: [{ order, source, anchor, providerPlaceId, name, address, roadAddress, category, categoryCode, lat, lng, placeUrl, savedPlaceId, walkMinutesFromPrevious }] }] }` |

요청 본문(JSON):

| 필드 | 규칙 |
|---|---|
| `anchor` | 필수. `{ "type": "REGION", "regionId": "HYEHWA" }` 또는 `{ "type": "PLACE", "placeId": 5 }`(내 보관함 장소 번호). 두 값을 함께 보내면 400 |
| `placeCount` | 필수, 3~5 |
| `categorySequence` | 필수. 방문 순서별 카테고리 코드 배열이고 **길이가 `placeCount`와 같아야** 한다. 코드: `MEAL`(맛집), `CAFE`(카페), `BAR`(술집), `ACTIVITY`(놀거리), `CULTURE`(전시·문화), `WALK`(산책), `SHOPPING`(쇼핑) |
| `requiredPlaceOrder` | `PLACE` 기준이면 필수(1~`placeCount`): 그 보관함 장소가 몇 번째로 방문하는지. `REGION` 기준이면 보내지 않는다 |
| `excludeCandidateKeys` | 선택(최대 50개). 다시 추천받을 때 앞서 받은 `candidateKey`를 모두 담는다 |

- 지원 지역 `regionId`(화면 이름도 받지만 영문 코드를 권장): 잠실 `JAMSIL`, 성신여대입구역 `SUNGSHIN`, 한성대입구역 `HANSUNG`, 혜화 `HYEHWA`, 서촌 `SEOCHON`, 연남 `YEONNAM`.
- 추천은 AI 서버가 계산한다. 코스는 최대 3개이고 이웃 장소 사이 도보 시간은 어림값이다. `source`는 `PERSONAL`(내 보관함) 또는 `EXTERNAL`(외부 후보), `anchor`는 기준·필수 장소 여부(true/false), `categoryCode`는 위 7개 코드, `category`는 화면에 보일 이름이다. `savedPlaceId`는 `PERSONAL`일 때만 값이 있다.
- `NOT_ENOUGH_CANDIDATES`(후보 부족)는 오류가 아니라 200이며 `reason`(`INSUFFICIENT_PLACES`, `UNSUPPORTED_REGION`, `NO_MORE_COURSES`)과 빈 `courses`가 온다. 서버가 지역을 자동으로 넓히지 않는다.
- AI 추천 서버가 응답하지 않으면 다른 방식으로 대신하지 않고 502(`RECOMMENDATION-0502`)를 돌려준다. 읽기 제한은 15초이다.
- 추천 결과를 개인 코스에 저장하려면 앱에서 확정한 장소를 [코스 요청에 담는다](#코스-추천-결과-저장). `candidateKey`는 다시 추천받을 때 `excludeCandidateKeys`에 사용한다.
- 분위기 정보로 거르는 기능은 아직 없다(외부 요약 연동 정책 미정).

### 코스 추천 결과 저장

`POST /api/v1/courses`로 코스를 만들고, `PUT /api/v1/courses/{courseId}`로 수정한다. 두 요청 모두 UUID 형식의 `Idempotency-Key` 헤더와 방문 순서대로 정렬한 `places` 3~5개를 받는다.

추천 결과의 `providerPlaceId`, `name`, `address`, `lat`, `lng`를 그대로 `placeSnapshot`에 담는다. 아래는 생성 요청의 `places` 항목 하나다.

```json
{
  "placeSnapshot": {
    "providerPlaceId": "123456789",
    "name": "혜화책방",
    "address": "서울 종로구 대학로 101",
    "lat": 37.5815,
    "lng": 127.0021
  },
  "menuList": []
}
```

- 새 장소는 `placeSnapshot` 또는 내 보관함의 숫자 `placeId` 중 하나로 보낸다. 보관함 경로를 쓸 때는 추천 응답의 `savedPlaceId`를 `placeId`로 보낸다. 스냅샷에서 `name`, `lat`, `lng`는 필수이고 `providerPlaceId`, `address`는 생략하거나 `null`로 보낼 수 있다. 스냅샷으로 담은 장소는 코스에만 저장된다. `tags`는 보관함 API에서 사용한다.
- 수정할 때 새 장소에는 `coursePlaceId: null`을 추가한다. 유지할 장소는 상세 조회에서 받은 `coursePlaceId`와 `menuList`를 보낸다. 예전 앱이 함께 보내는 숫자 `placeId`는 조회된 값과 같아야 한다. 장소 자체를 바꾸려면 기존 항목을 요청에서 빼고 새 장소로 보낸다.
- `GET /api/v1/courses/{courseId}`의 장소 항목에는 `coursePlaceId`, `placeId`, `placeName`과 함께 `provider`, `providerPlaceId`, `address`, `latitude`, `longitude`가 온다. 스냅샷 장소는 `placeId`가 `null`이다. `providerPlaceId`가 있으면 `provider`는 `KAKAO`이고, 식별자가 없는 스냅샷은 두 필드가 `null`이다. 기존 보관함 기반 장소는 저장된 `placeId`와 `placeName`으로 조회되고, 스냅샷 필드는 `null`이다. 보관함에서 장소를 삭제한 뒤에도 `coursePlaceId`로 코스의 해당 항목을 유지하고 수정할 수 있다.

### 활동 기록·알림·푸시

| API | 상태 | 요청 | 응답 `data` |
|---|---|---|---|
| `GET /activities` | 구현 | `cursor`, `size` | `{ items: [{ activityId, type, targetType, targetId, summary, createdAt }], nextCursor, hasNext }`. `type`: `COURSE_SHARED`, `COURSE_SHARE_CANCELED`, `COURSE_DELETED`, `AMOUNT_CHANGED` |
| `GET /notifications` | 구현 | `cursor`, `size` | `{ items: [{ notificationId, type, title, body, targetType, targetId, read, createdAt }], unreadCount, nextCursor, hasNext }` |
| `PATCH /notifications/{id}/read` | 구현 | — | 읽음 처리된 알림 1건(여러 번 호출해도 안전) |
| `POST /devices` | 구현(#74) | `{ "platform": "ANDROID"\|"IOS", "token": "FCM 토큰" }` | 204. 등록은 멱등(같은 토큰 재등록 안전) |
| `POST /devices/unregister` | 구현(#74) | `{ "token": "..." }` | 204 |

- **아직 연결되지 않았다:** 서버의 공유·삭제 동작이 활동 기록·알림을 남기는 코드는 서비스가 제공되어 있지만, 코스·커플 쪽(재원)에서 호출하는 연결이 없다. 시연 전에 연결할 예정이며(코스 공유·공유 취소·삭제), 그 전까지는 실제로 생기는 알림이 없을 수 있다.
- **FCM 푸시 전송은 미구현**이다(키·의존성 필요). 시연에서는 알림 목록 조회(polling)로 진행하고, 실제 전송은 후속 작업이다.

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
| 5-1 | **이메일 가입·로그인** | 시연에 포함하기로 했다(2026-11-09). 지금 서버는 카카오·구글만 있다 | 재원이 추가하고 요청·응답은 확정 후 공유한다. 앱은 소셜 로그인과 별도 진입점을 준비 | 재원·앱 |

### 토큰·세션

| # | 안건 | 제안 | 함께 정할 사람 |
|---|---|---|---|
| 6 | 토큰 유효 시간 | 접근 30분, 갱신 14일. 로그인이 자주 풀리면 갱신을 늘린다 | 승진·재원·앱 |
| 6-1 | 갱신 요청 동시성 처리 | §2 토큰 처리 규칙 2번을 앱이 지킨다(한 번에 한 개의 갱신 요청) | 앱 담당 |

### 업로드·호출

| # | 안건 | 왜 정해야 하나 | 제안 | 함께 정할 사람 |
|---|---|---|---|---|
| 7 | **이미지 업로드 용량** | 서버 검증 상한은 5MB지만, **Spring 기본 업로드 한도가 파일당 1MB**라 설정을 바꾸기 전에는 1MB를 넘으면 413이다. 폰 스크린샷은 보통 1MB를 넘는다 | 서버 환경변수 `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=5MB`, `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=6MB`를 설정한다(재원 영역). 스크린샷 인식을 시연에 포함하기로 해 파일당 5MB, 요청 전체 6MB로 정해졌다. 설정 전까지 앱이 이미지를 긴 변 1280px·JPEG 품질 80 정도로 줄여 1MB 이하로 보낸다 | 재원·앱 |
| 8 | **장소 검색 호출 빈도** | 카카오 호출 일일 한도를 확인하지 못했다. 한 사용자가 글자를 칠 때마다 호출하면 한도에 걸릴 수 있다 | 자동완성 대신 **검색 버튼·엔터로 호출**하거나 입력 후 500ms이상 멈추면 호출(디바운스) | 앱 담당 |

### 탈퇴·계정

| # | 안건 | 왜 정해야 하나 | 제안 | 함께 정할 사람 |
|---|---|---|---|---|
| 9 | **회원 탈퇴** | 시연에 쓰이지 않아 **개발하지 않기로 했다**(2026-11-08). 서버에 `DELETE /members/me`가 없다. 앱 스토어 정식 출시 때는 앱 안 계정 삭제가 필요할 수 있다(정책 확인 필요) | 탈퇴 버튼은 숨긴다. 출시를 하게 되면 그때 탈퇴 정책을 정하고 구현 | 승진·재원·앱 |

### 코스·추천

| # | 안건 | 왜 정해야 하나 | 제안 | 함께 정할 사람 |
|---|---|---|---|---|
| 10 | **추천받은 코스 저장 방법** | 외부 추천 장소에는 `savedPlaceId`가 없다 | 확정한 장소를 [코스 요청의 `placeSnapshot`](#코스-추천-결과-저장)으로 보낸다. 조회·수정할 때는 코스에서 발급한 `coursePlaceId`를 사용한다 | 재원·앱 |
| 11 | 지원 지역 6곳의 화면 표기 | 서버는 화면 이름(`잠실` 등) 또는 영문 코드(`JAMSIL`)를 모두 받는다. 지역 목록을 내려주는 API는 없다 | **시연에서는 앱이 6곳을 고정으로 표시**하고 선택한 코드를 `anchor.regionId`로 보낸다(`JAMSIL`, `SUNGSHIN`, `HANSUNG`, `HYEHWA`, `SEOCHON`, `YEONNAM`). 목록을 서버에서 관리할 필요가 생기면 지역 목록 API를 추가 | 앱 담당 |

### 알림·푸시

| # | 안건 | 제안 | 함께 정할 사람 |
|---|---|---|---|
| 12 | **기기 토큰 등록 API 모양** | `POST /devices {platform, token}`, 해제는 `POST /devices/unregister {token}`(#74). 앱 실행·로그인 때와 토큰이 바뀔 때 등록, 로그아웃 때 해제. 다른 모양을 원하면 알려 달라 | 앱 담당 |
| 13 | **FCM 전송 시점** | 서버에 FCM 연동이 없다(Firebase 서비스 계정 키·의존성 필요). 시연은 알림 목록 조회로 진행하고 실제 전송은 후속 작업 | 재원·승진 |
| 14 | 푸시 페이로드 | 서버가 보낼 값: `title`, `body` + `data`(`type`, `targetType`, `targetId`). 앱이 탭 시 이동할 화면을 `targetType`로 구분 | 앱 담당 |

### 인프라

| # | 안건 | 제안 | 함께 정할 사람 |
|---|---|---|---|
| 15 | **개발 서버 주소·HTTPS** | 배포된 서버가 없다. 알맞은 안드로이드는 평문(HTTP) 요청이 기본으로 막히고 iOS도 유사하다. 실기기로 테스트하려면 내 PC의 주소나 터널이 필요하다 | 개발 중에는 개인 PC에서 서버를 띄우고 에뮬레이터(안드로이드 에뮬레이터는 `10.0.2.2:8080`)로 연결. 배포 서버와 HTTPS는 비용·일정과 함께 팀이 결정 | 재원·팀 전체 |
| 16 | **시간대** | 서버는 모두 UTC로 준다 | 앱이 한국 시간(KST)으로 변환해 표시 | 앱 담당 |

---

## 5. 아직 없는 것과 알고 있는 한계

- **실제 연동 검증이 없다.** 카카오 로그인은 실제 앱에서 받은 토큰으로 아직 시험하지 못했다. 틀린 토큰이 `MEMBER-0401`로 거절되는 것만 확인했다. 앱 팀과 함께 처음 로그인이 되는지 보는 시점이 필요하다.
- AI 서버(링크·이미지 인식, 코스 추천) 연동은 서버에 병합되어 있고, 11/11에 시연용 AI 서버로 직접 호출해 정상 응답을 확인했다. 아직 확인하지 않은 것은 앱 요청을 서버 API로 받아 AI 서버까지 가는 전체 흐름과 실제 스크린샷의 인식 품질이다. 인식이 실패하거나 AI 서버를 쓸 수 없을 때는 `NEEDS_SEARCH`가 올 수 있으므로 앱은 항상 직접 검색으로 이어지는 화면이 필요하다. 코스 추천은 AI 서버가 꺼져 있으면 502(`RECOMMENDATION-0502`)이다.
- 로그인 시도 횟수 제한, 기기별 로그아웃 관리, 계정 삭제는 없다.
- 서버 오류 메시지(`error.message`)는 한국어 안내 문구이다. 화면에 그대로 써도 되지만, 문구는 바뀔 수 있으므로 동작은 `error.code`로 나눠야 한다.

## 6. 앱 팀이 처음 연동할 때 순서 (제안)

1. 서버를 개인 PC에서 띄운다(`GET /actuator/health`가 200이면 준비).
2. 카카오 로그인 → `POST /auth/login/kakao` → 토큰 저장 → `GET /members/me`.
3. `GET /places/search` → `POST /saved-places` → `GET /saved-places`.
4. 토큰 만료 시나리오(접근 토큰을 일부러 짧게 하거나 지워 보기): 401 → 갱신 → 재시도.
5. 추천·알림·이미지 등은 위가 되면 이어서.
