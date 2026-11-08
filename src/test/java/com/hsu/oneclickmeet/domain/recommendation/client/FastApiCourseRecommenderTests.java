package com.hsu.oneclickmeet.domain.recommendation.client;

import com.hsu.oneclickmeet.domain.place.client.AiServerProperties;
import com.hsu.oneclickmeet.domain.recommendation.client.CourseRecommendationCommand.SavedPlaceInput;
import com.hsu.oneclickmeet.domain.recommendation.exception.RecommendationErrorCode;
import com.hsu.oneclickmeet.domain.recommendation.exception.RecommendationUpstreamException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FastApiCourseRecommenderTests {

	private static final String URL = "https://ai.test/internal/v1/course-recommendations";

	private static final String COURSES = """
		{"courses":[{"candidateKey":"crs_c68221c0fc","title":"연남 맛집·카페 코스","unknownField":1,
		  "places":[
		   {"visitOrder":1,"name":"연남 파스타집","address":"서울 마포구 연남로 1","latitude":37.5626,"longitude":126.9255,
		    "categoryCode":"MEAL","categoryLabel":"맛집","source":"PERSONAL","placeId":12,"isAnchor":false,
		    "sourceUrl":null,"provider":null,"providerPlaceId":null,"roadAddress":"서울 마포구 연남로 1","jibunAddress":null},
		   {"visitOrder":2,"name":" 연남 카페 ","address":"서울 마포구 연남동 2","latitude":37.5631,"longitude":126.9282,
		    "categoryCode":"CAFE","categoryLabel":"카페","source":"EXTERNAL","placeId":null,"isAnchor":true,
		    "sourceUrl":"http://place.map.kakao.com/987","provider":"KAKAO","providerPlaceId":"987",
		    "roadAddress":"서울 마포구 연남로 2","jibunAddress":"서울 마포구 연남동 2-1"},
		   {"visitOrder":3,"name":"데모 공원","address":"데모 주소","latitude":37.56,"longitude":126.92,
		    "categoryCode":"WALK","categoryLabel":"산책","source":"EXTERNAL","placeId":null,"isAnchor":false,
		    "provider":"DEMO","providerPlaceId":"demo-1"}],
		  "walkingLegs":[{"fromVisitOrder":1,"toVisitOrder":2,"estimatedMinutes":5},
		                 {"fromVisitOrder":2,"toVisitOrder":3,"estimatedMinutes":7}],
		  "totalWalkingMinutes":12}],
		 "reason":null,"walkingTimeMethod":"COORDINATE_ESTIMATE"}""";

	private final RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.test");
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	private FastApiCourseRecommender recommender(String baseUrl) {
		return new FastApiCourseRecommender(builder.build(), new AiServerProperties(baseUrl, null, null, null, null, null));
	}

	private static SavedPlaceInput saved(long id, String categoryCode) {
		return new SavedPlaceInput(id, "내 장소 " + id, "주소 " + id, 37.5, 127.0, categoryCode);
	}

	private static CourseRecommendationCommand regionCommand() {
		return new CourseRecommendationCommand("YEONNAM", null, 3, List.of("MEAL", "CAFE", "WALK"), null,
			List.of(saved(12, "MEAL"), saved(13, null)), List.of("crs_old"));
	}

	private static void assertUpstream(Throwable thrown) {
		assertThat(thrown).isInstanceOf(RecommendationUpstreamException.class);
		assertThat(((RecommendationUpstreamException) thrown).getErrorCode())
			.isEqualTo(RecommendationErrorCode.UPSTREAM_FAILURE);
	}

	@Test
	@DisplayName("지역 기준 요청은 지역 ID와 카테고리 순서, 보관함 장소를 보내고 필수 장소 위치는 보내지 않는다")
	void sendsRegionRequest() {
		server.expect(requestTo(URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.anchor.type").value("REGION"))
			.andExpect(jsonPath("$.anchor.regionId").value("YEONNAM"))
			.andExpect(jsonPath("$.anchorPlace").doesNotExist())
			.andExpect(jsonPath("$.placeCount").value(3))
			.andExpect(jsonPath("$.categorySequence[0]").value("MEAL"))
			.andExpect(jsonPath("$.categorySequence[2]").value("WALK"))
			.andExpect(jsonPath("$.requiredPlaceOrder").doesNotExist())
			.andExpect(jsonPath("$.savedPlaces[0].placeId").value(12))
			.andExpect(jsonPath("$.savedPlaces[0].source").value("PERSONAL"))
			.andExpect(jsonPath("$.savedPlaces[0].categoryCode").value("MEAL"))
			.andExpect(jsonPath("$.savedPlaces[0].latitude").value(37.5))
			.andExpect(jsonPath("$.savedPlaces[1].categoryCode").doesNotExist())
			.andExpect(jsonPath("$.excludeCandidateKeys[0]").value("crs_old"))
			.andRespond(withSuccess(COURSES, MediaType.APPLICATION_JSON));

		recommender("https://ai.test").recommend(regionCommand());

		server.verify();
	}

	@Test
	@DisplayName("장소 기준 요청은 기준 장소와 필수 장소 위치를 보낸다")
	void sendsPlaceRequest() {
		server.expect(requestTo(URL))
			.andExpect(jsonPath("$.anchor.type").value("PLACE"))
			.andExpect(jsonPath("$.anchor.placeId").value(41))
			.andExpect(jsonPath("$.anchor.regionId").doesNotExist())
			.andExpect(jsonPath("$.anchorPlace.placeId").value(41))
			.andExpect(jsonPath("$.anchorPlace.name").value("내 장소 41"))
			.andExpect(jsonPath("$.requiredPlaceOrder").value(2))
			.andRespond(withSuccess(COURSES, MediaType.APPLICATION_JSON));

		recommender("https://ai.test").recommend(new CourseRecommendationCommand(null, saved(41, "CE7"), 3,
			List.of("MEAL", "CAFE", "WALK"), 2, List.of(), List.of()));

		server.verify();
	}

	@Test
	@DisplayName("응답을 앱에서 쓸 값으로 옮긴다: 도보 시간은 도착 순서 기준, 카카오가 아닌 제공자의 ID는 버린다")
	void mapsCourses() {
		server.expect(requestTo(URL)).andRespond(withSuccess(COURSES, MediaType.APPLICATION_JSON));

		CourseRecommendations result = recommender("https://ai.test").recommend(regionCommand());

		assertThat(result.reason()).isNull();
		assertThat(result.courses()).hasSize(1);
		var course = result.courses().get(0);
		assertThat(course.candidateKey()).isEqualTo("crs_c68221c0fc");
		assertThat(course.title()).isEqualTo("연남 맛집·카페 코스");
		assertThat(course.totalWalkMinutes()).isEqualTo(12);
		var first = course.places().get(0);
		assertThat(first.source()).isEqualTo("PERSONAL");
		assertThat(first.placeId()).isEqualTo(12L);
		assertThat(first.anchor()).isFalse();
		assertThat(first.walkMinutesFromPrevious()).isNull();
		assertThat(first.address()).isEqualTo("서울 마포구 연남로 1");
		var second = course.places().get(1);
		assertThat(second.name()).isEqualTo("연남 카페");
		assertThat(second.anchor()).isTrue();
		assertThat(second.providerPlaceId()).isEqualTo("987");
		assertThat(second.address()).isEqualTo("서울 마포구 연남동 2-1");
		assertThat(second.roadAddress()).isEqualTo("서울 마포구 연남로 2");
		assertThat(second.categoryLabel()).isEqualTo("카페");
		assertThat(second.walkMinutesFromPrevious()).isEqualTo(5);
		var third = course.places().get(2);
		assertThat(third.providerPlaceId()).isNull();
		assertThat(third.walkMinutesFromPrevious()).isEqualTo(7);
	}

	@Test
	@DisplayName("코스가 없으면 빈 목록과 이유를 돌려준다")
	void returnsReasonWhenNoCourses() {
		server.expect(requestTo(URL))
			.andRespond(withSuccess("{\"courses\":[],\"reason\":\"INSUFFICIENT_PLACES\"}", MediaType.APPLICATION_JSON));

		CourseRecommendations result = recommender("https://ai.test").recommend(regionCommand());

		assertThat(result.courses()).isEmpty();
		assertThat(result.reason()).isEqualTo("INSUFFICIENT_PLACES");
	}

	@Test
	@DisplayName("서비스 키가 설정되면 X-AI-Key 헤더로 보낸다")
	void sendsServiceKeyHeader() {
		AiServerProperties properties = new AiServerProperties("https://ai.test", "secret", null, null, null, null);
		RestClient.Builder keyed = RestClient.builder().baseUrl("https://ai.test");
		keyed.defaultHeader("X-AI-Key", properties.serviceKey());
		MockRestServiceServer keyedServer = MockRestServiceServer.bindTo(keyed).build();
		keyedServer.expect(requestTo(URL)).andExpect(header("X-AI-Key", "secret"))
			.andRespond(withSuccess(COURSES, MediaType.APPLICATION_JSON));

		new FastApiCourseRecommender(keyed.build(), properties).recommend(regionCommand());

		keyedServer.verify();
		assertThat(properties.recommendReadTimeout()).isEqualTo(Duration.ofSeconds(15));
	}

	@Test
	@DisplayName("AI 서버가 400·401·404로 거절해도 502로 바꾸고 다시 시도하지 않는다")
	void clientErrorsBecomeUpstreamFailureWithoutRetry() {
		for (HttpStatus status : List.of(HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED, HttpStatus.NOT_FOUND)) {
			MockRestServiceServer single = MockRestServiceServer.bindTo(builder).build();
			single.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(status));

			assertThatThrownBy(() -> recommender("https://ai.test").recommend(regionCommand()))
				.satisfies(FastApiCourseRecommenderTests::assertUpstream);
			single.verify();
		}
	}

	@Test
	@DisplayName("AI 서버 5xx는 다시 시도하지 않고 502로 바꾼다")
	void serverErrorBecomesUpstreamFailureWithoutRetry() {
		server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		assertThatThrownBy(() -> recommender("https://ai.test").recommend(regionCommand()))
			.satisfies(FastApiCourseRecommenderTests::assertUpstream);
		server.verify();
	}

	@Test
	@DisplayName("연결 실패·시간 초과는 502로 바꾼다")
	void networkFailureBecomesUpstreamFailure() {
		server.expect(requestTo(URL)).andRespond(withException(new IOException("timeout")));

		assertThatThrownBy(() -> recommender("https://ai.test").recommend(regionCommand()))
			.satisfies(FastApiCourseRecommenderTests::assertUpstream);
	}

	@Test
	@DisplayName("읽을 수 없거나 형식이 맞지 않는 응답은 502로 바꾼다")
	void badResponseBecomesUpstreamFailure() {
		for (String body : List.of(
			"not json",
			"{}",
			"{\"courses\":[{\"candidateKey\":\"\",\"places\":[]}]}",
			"{\"courses\":[{\"candidateKey\":\"k\",\"places\":[{\"visitOrder\":1,\"name\":\"좌표 없음\"}]}]}")) {
			MockRestServiceServer single = MockRestServiceServer.bindTo(builder).build();
			single.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

			assertThatThrownBy(() -> recommender("https://ai.test").recommend(regionCommand()))
				.satisfies(FastApiCourseRecommenderTests::assertUpstream);
		}
	}

	@Test
	@DisplayName("AI 서버 주소가 설정되지 않았으면 호출하지 않고 502로 바꾼다")
	void failsWhenNotConfigured() {
		assertThatThrownBy(() -> recommender("").recommend(regionCommand()))
			.satisfies(FastApiCourseRecommenderTests::assertUpstream);
		server.verify();
	}
}
