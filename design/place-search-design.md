# 장소 검색 설계

- 범위: `GET /api/v1/places/search` — 장소 이름·주소로 검색해 **후보 목록만** 돌려준다. 저장은 하지 않는다(저장은 `POST /api/v1/saved-places`).
- 담당: 승진 (`domain/place`)
- 상태: **구현 완료(2026-11-05 갱신).** 초안의 가정 중 바뀐 것은 아래 "초안 대비 바뀐 점"에 모았다.
- 구현: #22(클라이언트), #58(서비스·API), #60(보관함), #64(링크·이미지로 장소 찾기), #72(주변 놀거리·호출 수 지표)

## 초안 대비 바뀐 점

| 항목 | 초안 | 구현 |
|---|---|---|
| 경로 | `/places/search` | `/api/v1/places/search` (모든 API가 `/api/v1` 아래) |
| 카카오 응답·한도 | 기억에 의존, 확인 필요 | **2026-10-30 실제 호출로 확인**(아래 "카카오 응답 사실") |
| 페이지 상한 규칙 | `page` 상한은 카카오 제한에 맞춤(미확정) | 요청한 쪽의 첫 항목 위치 `(page-1)×size`가 45 이상이면 카카오를 호출하지 않고 빈 결과(200). 초안의 `page×size`가 아님 |
| `lat`·`lng` | 선택 파라미터 | **제거.** 보내도 무시하며 카카오에 위치를 전달하지 않는다(실시간 위치를 수집하지 않는 방침과 맞추기 위해) |
| 예외 기반 | #13 기반 예외를 상속 예정 | `PlaceUpstreamException`이 `BusinessException`을 상속(#13은 #25로 병합됨) |
| 로그인 | ②에서 보호 | #68(Security·JWT)로 로그인이 필요하다. 토큰이 없거나 올바르지 않으면 JSON 401(`COMMON-0401`) |
| 호출 수 지표(Micrometer) | PR 3에서 추가 | #72로 구현(아래 §4) |

## 1. 흐름

```text
앱 ──GET /api/v1/places/search?query=…&page=1──▶ PlaceSearchController
                                           │ 입력 검증 (query 1~100자, page ≥ 1, size 1~15)
                                           ▼
                                      PlaceSearchService
                                           │ 질의어 정규화(앞뒤 공백 제거), 페이지 상한 확인
                                           ▼
                                  PlaceSearchClient (인터페이스)
                                           │
                                           ▼
                                  KakaoPlaceSearchClient ──HTTPS──▶ 카카오 로컬 API
                                           │ 응답 → 내부 후보 모델로 변환
                                           ▼
                                  PlaceSearchResponse (후보 목록 + 다음 페이지 여부)
```

- DB를 읽지도 쓰지도 않는다. 같은 검색을 반복해도 부작용이 없다.
- 후보 0건은 **빈 목록 `[]`과 200**으로 응답한다. 인식 실패 여부는 앱이 판단한다.
- 동명 장소는 걸러내지 않고 모두 돌려준다(사용자가 고른다).

## 2. 패키지와 클래스

`com.hsu.oneclickmeet.domain.place`

| 위치 | 클래스 | 역할 |
|---|---|---|
| `controller` | `PlaceSearchController` | 요청 바인딩·입력 검증·서비스 호출·응답 구성 |
| `dto.request` | `PlaceSearchRequest` (record) | `query`, `page`(기본 1), `size`(기본 10) |
| `dto.response` | `PlaceCandidateResponse`, `PlaceSearchResponse` (record) | 후보 1건, 후보 목록 + `page` + `hasNext` |
| `service` | `PlaceSearchService` | 정규화, 페이지 상한 확인, 클라이언트 호출, 응답 조립 |
| `client` | `PlaceSearchClient` (interface), `PlaceSearchCommand`, `PlaceSearchResult`, `PlaceCandidate` | 검색 경계와 입출력 모델 |
| `client` | `KakaoPlaceSearchClient` | 카카오 호출 구현(`RestClient`) |
| `client` | `KakaoLocalProperties`, `PlaceClientConfig` | 설정(키·주소·타임아웃)과 `RestClient` 구성 |
| `client.kakao` | `KakaoKeywordSearchResponse` (record) | 카카오 응답 모양. 밖으로 노출하지 않음 |
| `exception` | `PlaceUpstreamException`, `PlaceErrorCode` | 외부 호출 실패, 도메인 오류 코드 |

- `PlaceSearchClient` 인터페이스를 둔 이유: 서비스·컨트롤러 테스트에서 가짜 구현으로 바꿔 끼우기 위해서다. 링크·이미지로 장소 찾기(`PlaceResolveService`), 주변 놀거리(`NearbyPlaceService`), 코스 추천(`CourseRecommendationService`)도 같은 클라이언트를 쓴다.

## 3. API 계약

요청: `GET /api/v1/places/search?query=한성대입구역 카페&page=1&size=10`

| 파라미터 | 규칙 |
|---|---|
| `query` | 필수, 공백 제거 후 1~100자 |
| `page` | 기본 1, 1 이상 |
| `size` | 기본 10, 1~15 |

- `lat`, `lng`를 보내도 무시한다(카카오에 전달하지 않는다).

성공 `200 OK`:

```json
{
  "success": true,
  "data": {
    "candidates": [
      {
        "providerPlaceId": "1424427941",
        "name": "연우재",
        "address": "서울 성북구 성북동1가 61",
        "roadAddress": "서울 성북구 성북로 15-6",
        "category": "음식점 > 카페 > 테마카페 > 갤러리카페",
        "categoryGroupCode": "CE7",
        "lat": 37.589410369487,
        "lng": 127.00443388177,
        "placeUrl": "http://place.map.kakao.com/1424427941"
      }
    ],
    "page": 1,
    "hasNext": true
  },
  "error": null
}
```

- `provider`는 응답에 포함하지 않는다(현재 카카오 하나). 저장 API는 카카오로 간주한다.
- 카카오 원본의 `phone` 등 쓰지 않는 필드는 읽지 않고 버린다. `roadAddress`가 빈 값이면 `null`이다.
- 후보가 없을 때: `candidates: []`, `hasNext: false`.

### 카카오 응답 사실 (2026-10-30 실제 호출로 확인)

- 응답 필드: `documents[]`의 `id`, `place_name`, `address_name`, `road_address_name`, `category_name`, `category_group_code`, `x`(경도), `y`(위도), `place_url`(`http://`), 그리고 `meta.is_end`, `meta.pageable_count`. `x`·`y`는 문자열이다.
- 한 요청의 `size`는 최대 15이고, 한 검색어로 받을 수 있는 결과는 **최대 45건**이다(`pageable_count`).
- 결과 상한을 넘는 `page`를 요청하면 오류나 빈 목록이 아니라 **마지막 쪽을 다시 돌려준다**(`size=15`에서 `page=3`과 `page=4`의 결과가 같음을 확인).
  - 그래서 서버가 `(page-1)×size ≥ 45`이면 카카오를 호출하지 않고 빈 결과(200)로 응답하고, `hasNext`는 카카오의 `is_end`가 거짓이면서 `page×size < 45`일 때만 참이다.
- 확인하지 못한 것: 카카오 공식 문서의 일일 호출 한도와 약관.

### 오류

| 상황 | HTTP | 코드 |
|---|---|---|
| `query` 누락·길이 위반, `page`/`size` 범위 위반, 숫자가 아닌 값 | 400 | `COMMON-0400` (필드별 `errors` 포함) |
| 로그인 안 됨 | 401 | `COMMON-0401` |
| 카카오 호출 실패·한도 초과·잘못된 키·시간 초과·깨진 응답 | 502 | `PLACE-0502` |

- 업스트림 오류는 원인을 응답에 구분해 노출하지 않는다. 구분은 서버 로그에서 한다. 내부적으로 `AUTH`, `RATE_LIMIT`, `REQUEST_REJECTED`, `SERVER_ERROR`, `NETWORK`, `BAD_RESPONSE`로 분류하고, 키 오류(`AUTH`)와 요청 거부(`REQUEST_REJECTED`)는 개발자가 바로 알아야 하므로 `ERROR`, 나머지는 `WARN`으로 남긴다.

## 4. 외부 호출 정책 (구현값)

| 항목 | 값 |
|---|---|
| HTTP 클라이언트 | 스프링 `RestClient` |
| 타임아웃 | 연결 2초, 읽기 3초 |
| 재시도 | 연결 실패·5xx에 한해 1회. 4xx(한도 초과 포함)는 재시도 없음 |
| 로그 | 검색어 원문은 남기지 않는다(상태 코드·분류만) |
| 키 | 환경변수 `KAKAO_REST_API_KEY` → `kakao.local.rest-api-key`. 저장소에 두지 않는다. 키가 없어도 서버는 기동하고, 검색 호출은 카카오를 부르지 않고 `AUTH`로 실패한다 |
| 호출 수 지표 | `kakao.local.requests` 카운터. **실제로 보낸 요청마다**(재시도 포함) `outcome` 태그(`success`, `server_error`, `network`, `rate_limit`, `auth`, `request_rejected`, `bad_response`)와 함께 1 올린다. 키가 없어 요청을 보내지 않으면 세지 않는다(#72) |

- 한도 초과 시 앱이 직접 검색 외 다른 경로가 없다는 약점이 남아 있다. 지표를 외부 모니터링으로 내보내거나 경보를 거는 설정은 아직 없다.

## 5. 결정 사항

1. **저장 시 후보 검증: A안으로 시작(결정됨 10/30).** 저장 API는 앱이 고른 후보의 필드를 요청에 함께 받고, 서버는 카카오를 다시 호출하지 않고 형식·범위만 검증한다(필수값, 길이, 좌표 범위). 이후 장소를 `places`로 분리(B안)하면 서버 재검증 또는 서명 후보 토큰이 필요해진다.
2. 검색 지역은 제한하지 않는다(추천만 지원 지역 6곳으로 제한).
3. `lat`/`lng`는 쓰지 않는다(위 "초안 대비 바뀐 점"). 주변 놀거리는 사용자의 위치가 아니라 **저장된 장소의 좌표**를 쓴다.
4. `category`는 앱 화면용 분류로 변환하지 않고 카카오 값을 그대로 넘긴다(추천 카테고리 코드가 미정).

## 6. 후속 API 현황

| API | 상태 |
|---|---|
| `POST` `GET` `GET/{id}` `DELETE` `/api/v1/saved-places` | 구현(#60). 저장은 멱등(새로 저장 201, 이미 저장 200), 목록은 커서 방식, 타인의 항목은 404 |
| `POST /api/v1/places/resolve/link`, `/image` | 구현(#64). AI 서버 호출 계약은 협의 전 제안안, AI를 쓸 수 없으면 링크는 단순 규칙으로 대체·이미지는 `NEEDS_SEARCH` |
| `GET /api/v1/saved-places/{id}/nearby?category=CAFE\|RESTAURANT\|EXHIBITION\|PARK` | 구현(#72). 반경 1km, 가까운 순, 직선거리(`distanceMeters`) 포함, 기준 장소 제외 |
| `POST /api/v1/recommendations/courses` | 구현(#66). 결과는 저장하지 않으며 `candidateKey`로 저장하는 연결은 `course` 계약 합의 필요 |
| `POST /saved-places/{id}/share` | 미구현(`couple`/공유 영역과 합의 후) |

## 7. 테스트

실제 카카오 호출은 CI에서 하지 않는다. 키와 한도가 필요하고 결과가 흔들리기 때문이다.

| 대상 | 방법 | 확인한 것 |
|---|---|---|
| `KakaoPlaceSearchClient` | `MockRestServiceServer` | 정상 응답 변환, 실제 카카오 응답 모양, 빈 결과, 도로명주소 공백→null, 5xx 재시도, 429·401·400 분류(재시도 없음), 연결 실패, 깨진 JSON, 좌표 오류, 키 없음, 결과별 호출 수 |
| `PlaceSearchService` | 가짜 `PlaceSearchClient` | 정규화, 페이지 상한, 결과 조립 |
| `PlaceSearchController` | 독립 MockMvc + 가짜 서비스 | 입력 검증 400, 성공 응답 모양, 502 응답에 내부 상세가 없음 |

## 8. 남은 일

- [ ] 카카오 일일 한도·약관 확인, 호출 수 지표의 외부 노출·경보
- [ ] 서버를 실제 키로 띄워 카카오와 붙는 end-to-end 확인(지금은 가짜 응답과 단발 직접 호출로만 확인)
- [ ] AI 서버 호출 계약 확정(경로·필드)
