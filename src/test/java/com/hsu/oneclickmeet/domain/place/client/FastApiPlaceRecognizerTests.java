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
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FastApiPlaceRecognizerTests {

	private static final String TEXT_URL = "https://ai.test/internal/v1/place-recognitions";
	private static final String IMAGE_URL = "https://ai.test/internal/v1/place-image-recognitions";

	private static final String FOUND = """
		{"status":"CANDIDATES_FOUND","extractedQuery":"오지다","unknownField":1,"candidates":[
		 {"name":" 오지다 ","address":"서울특별시 송파구 백제고분로48길 24","latitude":37.5131,"longitude":127.1123,
		  "sourceUrl":"http://place.map.kakao.com/123","provider":"KAKAO","providerPlaceId":"123",
		  "roadAddress":"서울특별시 송파구 백제고분로48길 24","jibunAddress":"서울 송파구 방이동 44-1",
		  "categoryLabel":"맛집","categoryCode":"MEAL","matchType":"EXACT","matchReasons":["주소 일치","이름 일치"],
		  "tags":[{"label":"음식이 맛있어요","mentionCount":12},{"label":"양이 많아요","mentionCount":5}],"tagPostCount":31}
		]}""";

	private final RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.test");
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	private FastApiPlaceRecognizer recognizer(String baseUrl) {
		return new FastApiPlaceRecognizer(builder.build(), new AiServerProperties(baseUrl, null, null, null, null));
	}

	private static void assertReason(Throwable thrown, Reason reason) {
		assertThat(thrown).isInstanceOf(PlaceUpstreamException.class);
		assertThat(((PlaceUpstreamException) thrown).getReason()).isEqualTo(reason);
	}

	@Test
	@DisplayName("sharedText를 JSON으로 보내고 후보를 보관함 저장용 필드로 옮긴다")
	void sendsTextAndMapsCandidates() {
		server.expect(requestTo(TEXT_URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.sharedText").value("공유 텍스트"))
			.andRespond(withSuccess(FOUND, MediaType.APPLICATION_JSON));

		Recognition result = recognizer("https://ai.test").recognizeText("공유 텍스트");

		assertThat(result.extractedQuery()).isEqualTo("오지다");
		assertThat(result.places()).hasSize(1);
		var place = result.places().get(0);
		PlaceCandidate c = place.candidate();
		assertThat(c.providerPlaceId()).isEqualTo("123");
		assertThat(c.name()).isEqualTo("오지다");
		assertThat(c.address()).isEqualTo("서울 송파구 방이동 44-1");
		assertThat(c.roadAddress()).isEqualTo("서울특별시 송파구 백제고분로48길 24");
		assertThat(c.category()).isEqualTo("맛집");
		assertThat(c.categoryGroupCode()).isEqualTo("FD6");
		assertThat(c.lat()).isEqualTo(37.5131);
		assertThat(c.lng()).isEqualTo(127.1123);
		assertThat(c.placeUrl()).isEqualTo("http://place.map.kakao.com/123");
		assertThat(place.matchType()).isEqualTo("EXACT");
		assertThat(place.matchReasons()).containsExactly("주소 일치", "이름 일치");
		assertThat(place.tags()).extracting(Recognition.Tag::label).containsExactly("음식이 맛있어요", "양이 많아요");
		server.verify();
	}

	@Test
	@DisplayName("지번 주소가 없으면 도로명 주소를 주소로 쓴다")
	void usesRoadAddressWhenJibunMissing() {
		server.expect(requestTo(TEXT_URL)).andRespond(withSuccess(
			FOUND.replace("\"jibunAddress\":\"서울 송파구 방이동 44-1\"", "\"jibunAddress\":null"), MediaType.APPLICATION_JSON));

		var c = recognizer("https://ai.test").recognizeText("x").places().get(0).candidate();

		assertThat(c.address()).isEqualTo("서울특별시 송파구 백제고분로48길 24");
	}

	@Test
	@DisplayName("블로그 글이 3개 미만이면 태그를 숨기고, 태그는 최대 5개다")
	void hidesWeakTagsAndCapsTagCount() {
		server.expect(requestTo(TEXT_URL)).andRespond(withSuccess(
			FOUND.replace("\"tagPostCount\":31", "\"tagPostCount\":2"), MediaType.APPLICATION_JSON));
		assertThat(recognizer("https://ai.test").recognizeText("x").places().get(0).tags()).isEmpty();
		server.reset();

		String sixTags = "[" + "{\"label\":\"a\",\"mentionCount\":1},".repeat(5) + "{\"label\":\"b\",\"mentionCount\":1}]";
		server.expect(requestTo(TEXT_URL)).andRespond(withSuccess(
			FOUND.replaceAll("\"tags\":\\[.*?\\],\"tagPostCount\"", "\"tags\":" + sixTags + ",\"tagPostCount\""),
			MediaType.APPLICATION_JSON));
		assertThat(recognizer("https://ai.test").recognizeText("x").places().get(0).tags()).hasSize(5);
	}

	@Test
	@DisplayName("카카오 장소 ID가 없거나 카카오가 아닌 제공자의 후보는 저장할 수 없어 버린다")
	void dropsCandidatesThatCannotBeSaved() {
		String body = """
			{"status":"CANDIDATES_FOUND","candidates":[
			 {"name":"a","address":"x","latitude":1.0,"longitude":2.0,"provider":"KAKAO","providerPlaceId":null},
			 {"name":"b","address":"x","latitude":1.0,"longitude":2.0,"provider":"DEMO","providerPlaceId":"9"},
			 {"name":"c","address":"x","latitude":1.0,"longitude":2.0,"provider":null,"providerPlaceId":"8"},
			 {"name":"d","address":"x","latitude":1.0,"longitude":2.0,"provider":"KAKAO","providerPlaceId":"7"}
			]}""";
		server.expect(requestTo(TEXT_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		Recognition result = recognizer("https://ai.test").recognizeText("x");

		assertThat(result.places()).extracting(p -> p.candidate().providerPlaceId()).containsExactly("7");
	}

	@Test
	@DisplayName("NEEDS_SEARCH면 후보 없이 extractedQuery만 돌려준다")
	void needsSearchKeepsExtractedQuery() {
		server.expect(requestTo(TEXT_URL)).andRespond(withSuccess(
			"{\"status\":\"NEEDS_SEARCH\",\"extractedQuery\":\"연남 카페\",\"candidates\":[]}", MediaType.APPLICATION_JSON));

		Recognition result = recognizer("https://ai.test").recognizeText("x");

		assertThat(result.extractedQuery()).isEqualTo("연남 카페");
		assertThat(result.places()).isEmpty();
	}

	@Test
	@DisplayName("이미지는 multipart로 보낸다")
	void sendsImageAsMultipart() {
		server.expect(requestTo(IMAGE_URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Content-Type", org.hamcrest.Matchers.startsWith("multipart/form-data")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"image\"")))
			.andRespond(withSuccess(FOUND, MediaType.APPLICATION_JSON));

		Recognition result = recognizer("https://ai.test").recognizeImage(new byte[] {1, 2, 3}, "image/png");

		assertThat(result.places()).hasSize(1);
	}

	@Test
	@DisplayName("주소가 설정되지 않았으면 호출하지 않고 실패한다")
	void failsWithoutCallingWhenNotConfigured() {
		assertThatThrownBy(() -> recognizer("").recognizeText("x")).satisfies(thrown -> assertReason(thrown, Reason.AUTH));
		server.verify();
	}

	@Test
	@DisplayName("5xx와 연결 실패는 재시도 없이 한 번만 호출하고 실패한다")
	void doesNotRetryServerErrorOrNetworkFailure() {
		server.expect(ExpectedCount.once(), requestTo(TEXT_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
		assertThatThrownBy(() -> recognizer("https://ai.test").recognizeText("x"))
			.satisfies(thrown -> assertReason(thrown, Reason.SERVER_ERROR));
		server.verify();
		server.reset();

		server.expect(ExpectedCount.once(), requestTo(TEXT_URL)).andRespond(withException(new IOException("down")));
		assertThatThrownBy(() -> recognizer("https://ai.test").recognizeText("x"))
			.satisfies(thrown -> assertReason(thrown, Reason.NETWORK));
		server.verify();
	}

	@Test
	@DisplayName("4xx는 원인별로 분류한다")
	void classifiesClientErrors() {
		server.expect(ExpectedCount.once(), requestTo(TEXT_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		assertThatThrownBy(() -> recognizer("https://ai.test").recognizeText("x"))
			.satisfies(thrown -> assertReason(thrown, Reason.RATE_LIMIT));
		server.reset();

		server.expect(ExpectedCount.once(), requestTo(TEXT_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
		assertThatThrownBy(() -> recognizer("https://ai.test").recognizeText("x"))
			.satisfies(thrown -> assertReason(thrown, Reason.AUTH));
		server.reset();

		server.expect(ExpectedCount.once(), requestTo(TEXT_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
		assertThatThrownBy(() -> recognizer("https://ai.test").recognizeText("x"))
			.satisfies(thrown -> assertReason(thrown, Reason.REQUEST_REJECTED));
	}

	@Test
	@DisplayName("status가 없거나 깨진 JSON이면 BAD_RESPONSE다")
	void rejectsMalformedResponses() {
		server.expect(ExpectedCount.once(), requestTo(TEXT_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> recognizer("https://ai.test").recognizeText("x"))
			.satisfies(thrown -> assertReason(thrown, Reason.BAD_RESPONSE));
		server.reset();

		server.expect(ExpectedCount.once(), requestTo(TEXT_URL)).andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> recognizer("https://ai.test").recognizeText("x"))
			.satisfies(thrown -> assertReason(thrown, Reason.BAD_RESPONSE));
	}

	@Test
	@DisplayName("서비스 키가 설정되면 X-AI-Key 헤더로 보낸다")
	void sendsServiceKeyHeader() {
		AiServerProperties properties = new AiServerProperties("https://ai.test", "secret", Duration.ofSeconds(1), null, null);
		RestClient.Builder keyed = RestClient.builder().baseUrl("https://ai.test");
		if (properties.hasServiceKey()) {
			keyed.defaultHeader("X-AI-Key", properties.serviceKey());
		}
		MockRestServiceServer keyedServer = MockRestServiceServer.bindTo(keyed).build();
		keyedServer.expect(requestTo(TEXT_URL)).andExpect(header("X-AI-Key", "secret"))
			.andRespond(withSuccess(FOUND, MediaType.APPLICATION_JSON));

		new FastApiPlaceRecognizer(keyed.build(), properties).recognizeText("x");

		keyedServer.verify();
		assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(30));
	}
}
