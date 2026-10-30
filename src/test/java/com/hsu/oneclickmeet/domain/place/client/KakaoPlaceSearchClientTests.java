package com.hsu.oneclickmeet.domain.place.client;

import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException;
import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException.Reason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoPlaceSearchClientTests {

	private static final String SEARCH_URL_PREFIX = "https://dapi.kakao.com/v2/local/search/keyword.json";
	private static final String ONE_DOCUMENT = """
		{
		  "meta": {"total_count": 40, "pageable_count": 40, "is_end": false},
		  "documents": [
		    {
		      "id": "123456789",
		      "place_name": "예시 카페",
		      "category_name": "음식점 > 카페",
		      "category_group_code": "CE7",
		      "category_group_name": "카페",
		      "phone": "02-000-0000",
		      "address_name": "서울 성북구 삼선동 1",
		      "road_address_name": "서울 성북구 한성로 12",
		      "x": "127.0105",
		      "y": "37.5822",
		      "place_url": "https://place.map.kakao.com/123456789",
		      "distance": ""
		    }
		  ]
		}
		""";

	// 실제 카카오 응답에서 가져온 모양(공개 장소 정보). 전화번호는 개인정보 가능성이 있어 비웠다.
	private static final String REAL_RESPONSE = """
		{"documents":[
		{"address_name":"서울 성북구 성북동1가 61","category_group_code":"CE7","category_group_name":"카페",
		 "category_name":"음식점 \\u003e 카페 \\u003e 테마카페 \\u003e 갤러리카페","distance":"","id":"1424427941","phone":"",
		 "place_name":"연우재","place_url":"http://place.map.kakao.com/1424427941",
		 "road_address_name":"서울 성북구 성북로 15-6","x":"127.00443388177","y":"37.589410369487"},
		{"address_name":"서울 성북구 성북동1가 16-1","category_group_code":"CE7","category_group_name":"카페",
		 "category_name":"음식점 \\u003e 카페","distance":"","id":"1427027625","phone":"",
		 "place_name":"카머","place_url":"http://place.map.kakao.com/1427027625",
		 "road_address_name":"서울 성북구 성북로5길 17","x":"127.00405001793997","y":"37.588822035178396"}],
		"meta":{"is_end":false,"pageable_count":45,
		 "same_name":{"keyword":"카페","region":[],"selected_region":"서울 성북구 한성대입구역"},"total_count":217}}
		""";
	private static final String EMPTY = """
		{"meta": {"is_end": true}, "documents": []}
		""";

	private final RestClient.Builder builder = RestClient.builder().baseUrl("https://dapi.kakao.com");
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	private KakaoPlaceSearchClient client(String apiKey) {
		return new KakaoPlaceSearchClient(builder.build(), new KakaoLocalProperties(apiKey, null, null, null));
	}

	private static PlaceSearchCommand command() {
		return new PlaceSearchCommand("한성대입구역 카페", 1, 10, null, null);
	}

	private static void assertReason(Throwable thrown, Reason reason) {
		assertThat(thrown).isInstanceOf(PlaceUpstreamException.class);
		assertThat(((PlaceUpstreamException) thrown).getReason()).isEqualTo(reason);
	}

	@Test
	@DisplayName("검색 결과를 후보로 변환하고 인증 헤더와 검색어를 보낸다")
	void mapsDocumentsToCandidatesAndSendsAuthHeaderAndQuery() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(SEARCH_URL_PREFIX)))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header("Authorization", "KakaoAK test-key"))
			.andExpect(queryParam("query", "%ED%95%9C%EC%84%B1%EB%8C%80%EC%9E%85%EA%B5%AC%EC%97%AD%20%EC%B9%B4%ED%8E%98"))
			.andExpect(queryParam("page", "1"))
			.andExpect(queryParam("size", "10"))
			.andExpect(request -> assertThat(request.getURI().getRawQuery()).doesNotContainPattern("(^|&)[xy]="))
			.andRespond(withSuccess(ONE_DOCUMENT, MediaType.APPLICATION_JSON));

		PlaceSearchResult result = client("test-key").search(command());

		server.verify();
		assertThat(result.page()).isEqualTo(1);
		assertThat(result.hasNext()).isTrue();
		assertThat(result.candidates()).singleElement().satisfies(candidate -> {
			assertThat(candidate.providerPlaceId()).isEqualTo("123456789");
			assertThat(candidate.name()).isEqualTo("예시 카페");
			assertThat(candidate.address()).isEqualTo("서울 성북구 삼선동 1");
			assertThat(candidate.roadAddress()).isEqualTo("서울 성북구 한성로 12");
			assertThat(candidate.category()).isEqualTo("음식점 > 카페");
			assertThat(candidate.categoryGroupCode()).isEqualTo("CE7");
			assertThat(candidate.lat()).isEqualTo(37.5822);
			assertThat(candidate.lng()).isEqualTo(127.0105);
			assertThat(candidate.placeUrl()).isEqualTo("https://place.map.kakao.com/123456789");
		});
	}

	@Test
	@DisplayName("실제 카카오 응답 모양을 읽는다")
	void parsesRealKakaoResponseShape() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(SEARCH_URL_PREFIX)))
			.andRespond(withSuccess(REAL_RESPONSE, MediaType.APPLICATION_JSON));

		PlaceSearchResult result = client("test-key").search(new PlaceSearchCommand("카페", 1, 2, null, null));

		assertThat(result.hasNext()).isTrue();
		assertThat(result.candidates()).hasSize(2);
		PlaceCandidate first = result.candidates().get(0);
		assertThat(first.providerPlaceId()).isEqualTo("1424427941");
		assertThat(first.name()).isEqualTo("연우재");
		assertThat(first.category()).isEqualTo("음식점 > 카페 > 테마카페 > 갤러리카페");
		assertThat(first.roadAddress()).isEqualTo("서울 성북구 성북로 15-6");
		assertThat(first.lat()).isEqualTo(37.589410369487);
		assertThat(first.lng()).isEqualTo(127.00443388177);
		assertThat(first.placeUrl()).isEqualTo("http://place.map.kakao.com/1424427941");
	}

	@Test
	@DisplayName("위치가 있으면 x는 경도 y는 위도로 보낸다")
	void sendsLongitudeAsXAndLatitudeAsYWhenLocationIsGiven() {
		server.expect(queryParam("x", "127.01"))
			.andExpect(queryParam("y", "37.58"))
			.andRespond(withSuccess(EMPTY, MediaType.APPLICATION_JSON));

		client("test-key").search(new PlaceSearchCommand("카페", 1, 10, 37.58, 127.01));

		server.verify();
	}

	@Test
	@DisplayName("결과가 없으면 빈 목록과 마지막 페이지를 돌려준다")
	void returnsEmptyCandidatesAndLastPageWhenNoDocuments() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(SEARCH_URL_PREFIX)))
			.andRespond(withSuccess(EMPTY, MediaType.APPLICATION_JSON));

		PlaceSearchResult result = client("test-key").search(command());

		assertThat(result.candidates()).isEmpty();
		assertThat(result.hasNext()).isFalse();
	}

	@Test
	@DisplayName("도로명주소가 비어 있으면 null로 돌려준다")
	void returnsNullRoadAddressWhenBlank() {
		String body = ONE_DOCUMENT.replace("서울 성북구 한성로 12", "");
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(SEARCH_URL_PREFIX)))
			.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		PlaceSearchResult result = client("test-key").search(command());

		assertThat(result.candidates().get(0).roadAddress()).isNull();
	}

	@Test
	@DisplayName("서버 오류 후 재시도에 성공하면 결과를 돌려준다")
	void returnsResultWhenRetryAfterServerErrorSucceeds() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(SEARCH_URL_PREFIX)))
			.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(SEARCH_URL_PREFIX)))
			.andRespond(withSuccess(ONE_DOCUMENT, MediaType.APPLICATION_JSON));

		PlaceSearchResult result = client("test-key").search(command());

		server.verify();
		assertThat(result.candidates()).hasSize(1);
	}

	@Test
	@DisplayName("서버 오류가 반복되면 두 번만 호출하고 실패한다")
	void failsAfterTwoCallsWhenServerErrorRepeats() {
		server.expect(ExpectedCount.times(2), requestTo(org.hamcrest.Matchers.startsWith(SEARCH_URL_PREFIX)))
			.andRespond(withStatus(HttpStatus.BAD_GATEWAY));

		assertThatThrownBy(() -> client("test-key").search(command()))
			.satisfies(thrown -> assertReason(thrown, Reason.SERVER_ERROR));

		server.verify();
	}

	@Test
	@DisplayName("연결 실패가 반복되면 두 번만 호출하고 실패한다")
	void failsAfterTwoCallsWhenConnectionFailureRepeats() {
		server.expect(ExpectedCount.times(2), requestTo(org.hamcrest.Matchers.startsWith(SEARCH_URL_PREFIX)))
			.andRespond(request -> {
				throw new IOException("timeout");
			});

		assertThatThrownBy(() -> client("test-key").search(command()))
			.satisfies(thrown -> assertReason(thrown, Reason.NETWORK));

		server.verify();
	}

	@Test
	@DisplayName("호출 한도를 초과하면 재시도하지 않고 실패한다")
	void failsWithoutRetryWhenRateLimited() {
		server.expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.startsWith(SEARCH_URL_PREFIX)))
			.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

		assertThatThrownBy(() -> client("test-key").search(command()))
			.satisfies(thrown -> assertReason(thrown, Reason.RATE_LIMIT));

		server.verify();
	}

	@Test
	@DisplayName("인증 오류는 재시도하지 않고 AUTH로 분류한다")
	void classifiesUnauthorizedAsAuthWithoutRetry() {
		server.expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.startsWith(SEARCH_URL_PREFIX)))
			.andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertThatThrownBy(() -> client("wrong-key").search(command()))
			.satisfies(thrown -> assertReason(thrown, Reason.AUTH));

		server.verify();
	}

	@Test
	@DisplayName("요청이 거부되면 REQUEST REJECTED로 분류한다")
	void classifiesBadRequestAsRequestRejected() {
		server.expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.startsWith(SEARCH_URL_PREFIX)))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST));

		assertThatThrownBy(() -> client("test-key").search(command()))
			.satisfies(thrown -> assertReason(thrown, Reason.REQUEST_REJECTED));
	}

	@Test
	@DisplayName("응답 JSON이 깨졌으면 BAD RESPONSE로 분류한다")
	void classifiesMalformedJsonAsBadResponse() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(SEARCH_URL_PREFIX)))
			.andRespond(withSuccess("{ not json", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client("test-key").search(command()))
			.satisfies(thrown -> assertReason(thrown, Reason.BAD_RESPONSE));
	}

	@Test
	@DisplayName("좌표를 읽을 수 없으면 BAD RESPONSE로 분류한다")
	void classifiesUnreadableCoordinateAsBadResponse() {
		String body = ONE_DOCUMENT.replace("\"127.0105\"", "\"abc\"");
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(SEARCH_URL_PREFIX)))
			.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client("test-key").search(command()))
			.satisfies(thrown -> assertReason(thrown, Reason.BAD_RESPONSE));
	}

	@Test
	@DisplayName("API 키가 없으면 호출하지 않고 AUTH로 실패한다")
	void failsAsAuthWithoutCallingWhenApiKeyIsBlank() {
		assertThatThrownBy(() -> client(" ").search(command()))
			.satisfies(thrown -> assertReason(thrown, Reason.AUTH));

		server.verify();
	}
}
