# ③ 직접 검색 구조 설계 (초안)

- 범위: `GET /places/search` — 장소 이름·주소로 검색해 **후보 목록만** 돌려준다. 저장은 하지 않는다(저장은 `POST /saved-places`, 같은 이슈의 다음 단계).
- 담당: 승진 (`domain/place`)
- 선행 확인: ①의 장소 모델 합의(승진 결정: A안 `saved_places`), #13(전역 에러 핸들러)의 예외 기반 클래스
- **검증하지 못한 것:** 카카오 로컬 API 문서에 접속할 수 없어(이 세션의 네트워크 허용 목록 밖) 아래 카카오 요청·응답 필드와 한도는 **기억에 의존한 내용**이다. 구현 전에 공식 문서(키워드로 장소 검색하기)로 반드시 확인한다.

---

## 1. 흐름

```text
앱 ──GET /places/search?query=…&page=1──▶ PlaceSearchController
                                           │ 입력 검증 (query 1~100자, page/size 범위)
                                           ▼
                                      PlaceSearchService
                                           │ 질의어 정규화(앞뒤 공백 제거)
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
- 후보 0건은 **빈 목록 `[]`과 200**으로 응답한다. 인식 실패 여부는 앱이 판단한다(TRD 초안 4.3).
- 동명 장소는 걸러내지 않고 모두 돌려준다(사용자가 고른다).
- 인증된 사용자만 호출할 수 있다(인증 구현 전에는 열어두되, ②에서 보호).

## 2. 패키지와 클래스

`com.hsu.oneclickmeet.domain.place` (승진 소유 경로 — CODEOWNERS 확인)

| 위치 | 클래스 | 역할 |
|---|---|---|
| `controller` | `PlaceSearchController` | 요청 바인딩·입력 검증·서비스 호출·응답 구성만 담당 |
| `dto.request` | `PlaceSearchRequest` (record) | `query`, `page`(기본 1), `size`(기본 10), 선택: `lat`, `lng` |
| `dto.response` | `PlaceCandidateResponse` (record) | 후보 1건 |
| `dto.response` | `PlaceSearchResponse` (record) | `candidates`, `page`, `hasNext` |
| `service` | `PlaceSearchService` | 정규화, 클라이언트 호출, 응답 조립 |
| `client` | `PlaceSearchClient` (interface) | `search(PlaceSearchCommand)` → `PlaceSearchResult` |
| `client` | `KakaoPlaceSearchClient` | 카카오 호출 구현. `RestClient` 사용 |
| `client` | `KakaoLocalProperties` (`@ConfigurationProperties`) | 기본 URL, REST API 키, 타임아웃 |
| `client.kakao` | `KakaoKeywordSearchResponse` (record) | 카카오 응답 모양. 밖으로 노출하지 않음 |
| `exception` | `PlaceUpstreamException` 등 | 외부 호출 실패 (#13 기반 예외를 상속) |

- `PlaceSearchClient` 인터페이스를 둔 이유: 서비스·컨트롤러 테스트에서 가짜 구현으로 바꿔 끼우기 위해서다. 구현체를 하나만 쓰더라도 테스트 경계로서 필요하다. (컨벤션의 "Service는 구체 클래스로 시작"은 서비스 이야기이고, 외부 시스템 경계는 별개로 본다.)
- 같은 카카오 클라이언트를 `recommendation`(후보 반경 검색)도 쓸 예정이다. 그때 메서드를 추가하면 되고, 지금은 키워드 검색만 만든다.
- 엔티티는 이 단계에서 필요 없다.

## 3. API 계약 (요청/응답 래퍼 기준)

요청: `GET /places/search?query=한성대입구역 카페&page=1&size=10`

| 파라미터 | 규칙 |
|---|---|
| `query` | 필수, 공백 제거 후 1~100자 |
| `page` | 기본 1, 1 이상 (상한은 카카오 제한에 맞춤 — 확인 필요) |
| `size` | 기본 10, 1~15 (카카오 최대값에 맞춤 — 확인 필요) |
| `lat`, `lng` | 선택. 둘 다 있거나 둘 다 없어야 함. 있으면 근처 우선 정렬에 사용 |

성공 `200 OK`:

```json
{
  "success": true,
  "data": {
    "candidates": [
      {
        "providerPlaceId": "123456789",
        "name": "예시 카페",
        "address": "서울 성북구 …",
        "roadAddress": "서울 성북구 … 로 12",
        "category": "음식점 > 카페",
        "categoryGroupCode": "CE7",
        "lat": 37.5822,
        "lng": 127.0105,
        "placeUrl": "https://place.map.kakao.com/123456789"
      }
    ],
    "page": 1,
    "hasNext": false
  },
  "error": null
}
```

- `provider`는 응답에 포함하지 않는다(현재 카카오 하나). 저장 API가 필요로 하면 그때 추가한다.
- 카카오 원본의 `phone` 등 쓰지 않는 필드는 넘기지 않는다.
- 후보가 없을 때: `candidates: []`, `hasNext: false`.

오류:

| 상황 | HTTP | 코드(제안) |
|---|---|---|
| `query` 누락·길이 위반, `page/size` 범위 위반, `lat`/`lng` 짝 불일치 | 400 | `COMMON-0400` (필드별 `errors` 포함) |
| 로그인 안 됨 (②에서 적용) | 401 | `AUTH-0401` |
| 카카오 호출 실패·한도 초과·잘못된 키·시간 초과 | 502 | `PLACE-0502` |

- 업스트림 오류는 원인(잘못된 키, 한도 초과, 시간 초과)을 응답에 구분해 노출하지 않는다. 사용자에게는 "잠시 후 다시 시도하거나 직접 검색을 이용해 달라"는 일반 안내만 주고, 구분은 서버 로그와 지표로 한다. 키 오류는 개발자가 바로 알아야 하므로 `ERROR`, 한도·시간 초과는 `WARN`으로 남긴다.
- 코드 이름 `PLACE-0502`는 #13에서 정해질 규칙에 맞춰 조정한다.

## 4. 외부 호출 정책 (제안값, 구현 중 확인)

| 항목 | 제안 | 근거 |
|---|---|---|
| HTTP 클라이언트 | 스프링 `RestClient` | 추가 의존성 없이 시작(TRD 초안) |
| 타임아웃 | 연결 2초, 읽기 3초 | 검색은 입력 중 화면에서 쓰이므로 짧게. 실측 후 조정 |
| 재시도 | 연결 실패·5xx에 한해 1회, 4xx·429는 재시도 없음 | 한도 초과에서 재시도하면 상황이 악화됨 |
| 호출 수 기록 | `Micrometer` 카운터 `kakao.local.requests`, 태그 `outcome` | 비기능 요구(API별 호출 건수 기록). 의존성에 Actuator가 이미 있음 |
| 로그 | 질의어 원문은 남기지 않고 길이·결과 건수·소요 시간만 | 사용자 입력을 로그에 남기지 않는 비기능 요구 |
| 키 | 환경변수 `KAKAO_REST_API_KEY`를 `KakaoLocalProperties`로 주입 | 저장소에 키를 두지 않는 요구 |

- 키를 주입하려면 `application.yaml`과 `.env.example`에 설정 항목을 추가해야 한다. 두 파일은 **재원 소유 경로**이므로 해당 변경은 재원의 리뷰를 받아야 한다.
- 카카오 일일 한도와 앱 키 종류(REST API 키)는 공식 문서·개발자 콘솔에서 확인한다. 한도 초과 시에도 앱이 **직접 검색 외 다른 경로가 없다**는 점이 약점이다(직접 검색이 폴백 경로이기 때문). 호출 수를 지표로 보고 있다가 한도에 가까워지면 알 수 있게 한다.

## 5. 결정이 필요한 것

1. **저장 시 후보 검증 방식 — 결정됨(10/30): A안으로 시작**
   - 카카오 키워드 검색에는 "ID로 장소 1건 조회"가 없는 것으로 알고 있다(확인 필요). 그래서 저장 API는 앱이 고른 후보의 필드(`providerPlaceId`, 이름, 주소, 좌표, 카테고리 등)를 요청에 함께 받는다.
   - 장소 정보를 사용자별 `saved_places`에 저장하므로 잘못된 값의 영향이 본인에게만 있다. 따라서 서버가 카카오를 다시 호출해 검증하지 않고 **형식·범위 검증만** 한다(필수값, 길이, 좌표 범위).
   - 이후 장소를 `places`로 분리(B안)하면 여러 사용자가 같은 장소 정보를 공유하게 되므로, 그때는 서버 재검증(다시 검색해 확인) 또는 서명 후보 토큰이 필요해진다. 추가 비용은 카카오 호출 +1회/저장, 한도 소모, 저장 지연, 카카오 장애 시 저장 실패, 서명 키 관리 등이다.
2. 검색 지역을 제한할 것인가? 추천은 지원 지역 6곳으로 제한되지만, 장소 **저장**까지 6곳으로 막을지는 문서에서 확인하지 못했다. 제안: 제한하지 않는다.
3. `lat/lng`를 쓸 것인가? 지금은 선택 사항으로 두었다. 앱이 현재 위치를 보내지 않으면(실시간 위치는 수집하지 않는 방침) 쓰지 않는다. 쓰려면 위치를 수집하지 않는 방침과 충돌하지 않는지 확인한다.
4. `category`를 앱 화면용 분류로 변환할지: 추천 카테고리 코드는 미정이므로 이번에는 카카오 값을 그대로 넘긴다.

## 6. 테스트 계획

실제 카카오 호출은 CI에서 하지 않는다. 키와 한도가 필요하고 결과가 흔들리기 때문이다.

| 대상 | 방법 | 확인할 것 |
|---|---|---|
| `KakaoPlaceSearchClient` | 가짜 HTTP 서버(`MockRestServiceServer` 등) | 정상 응답 변환(필드·좌표 문자열→숫자), 0건, 마지막 페이지 표시, 4xx/5xx/429, 시간 초과, 깨진 JSON → 모두 업스트림 예외로 변환 |
| `PlaceSearchService` | 가짜 `PlaceSearchClient` | 질의어 정규화, 결과 조립, 클라이언트 예외 전달 |
| `PlaceSearchController` | `@WebMvcTest` + 가짜 서비스 | 입력 검증 400 (필드별 `errors`), 성공 응답 모양, 업스트림 실패 502 응답 모양 |
| 로그·지표 | 단위 테스트 | 질의어 원문이 로그에 없음, `outcome` 카운터 증가 |

각 테스트는 성공 경로와 실패 경로를 모두 가진다(AGENTS.md 기준).

## 7. 구현 순서 (PR 단위, 각 300~400줄 이내)

1. **PR 1 — 클라이언트:** `PlaceSearchClient`, `KakaoPlaceSearchClient`, 설정 클래스, 클라이언트 테스트. (`application.yaml`, `.env.example` 변경은 재원 리뷰)
2. **PR 2 — 서비스·API:** DTO, 서비스, 컨트롤러, 입력 검증, 서비스·컨트롤러 테스트. #13 머지 후 예외 처리 연결.
3. **PR 3 — 지표·로그:** 호출 수 카운터, 로그 규칙.

PR 1은 외부 계약이 아직 없는 내부 구현이라, CODEOWNERS 규칙상 담당자 직접 머지 범위에 들 가능성이 높다. 단 설정 파일 변경이 재원 경로이므로 리뷰 요청을 권장한다.

## 8. 시작 전에 확인할 것

- [ ] 카카오 공식 문서로 요청·응답 필드, `size`·`page` 상한, 한도를 확인한다.
- [ ] 카카오 개발자 콘솔에서 REST API 키를 발급받는다(키는 저장소에 올리지 않는다).
- [ ] #13의 예외 기반 클래스 이름을 확인한다.
- [x] 장소 모델은 A안(`saved_places`)으로 시작, 저장 시 형식·범위 검증만 한다(재원과 합의 확인 필요).
