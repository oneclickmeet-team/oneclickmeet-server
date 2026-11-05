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
import java.util.List;

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

class FastApiPlaceNameExtractorTests {

	private static final String TEXT_URL = "https://ai.test/v1/places/extract/text";
	private static final String IMAGE_URL = "https://ai.test/v1/places/extract/image";

	private final RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.test");
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	private FastApiPlaceNameExtractor extractor(String baseUrl) {
		return new FastApiPlaceNameExtractor(builder.build(), new AiServerProperties(baseUrl, null, null));
	}

	private static void assertReason(Throwable thrown, Reason reason) {
		assertThat(thrown).isInstanceOf(PlaceUpstreamException.class);
		assertThat(((PlaceUpstreamException) thrown).getReason()).isEqualTo(reason);
	}

	@Test
	@DisplayName("텍스트를 JSON으로 보내고 장소 이름을 다듬어(공백·중복·빈 값 제거) 돌려준다")
	void sendsTextAndCleansNames() {
		server.expect(requestTo(TEXT_URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.text").value("공유 텍스트"))
			.andRespond(withSuccess("{\"placeNames\":[\" 연우재 \",\"연우재\",\"\",null,\"카머\"]}", MediaType.APPLICATION_JSON));

		assertThat(extractor("https://ai.test").fromText("공유 텍스트")).containsExactly("연우재", "카머");
		server.verify();
	}

	@Test
	@DisplayName("이름은 최대 5개까지, 너무 긴 이름은 버린다")
	void limitsNamesAndDropsTooLong() {
		String longName = "가".repeat(101);
		server.expect(requestTo(TEXT_URL))
			.andRespond(withSuccess("{\"placeNames\":[\"" + longName + "\",\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]}", MediaType.APPLICATION_JSON));

		assertThat(extractor("https://ai.test").fromText("x")).containsExactly("1", "2", "3", "4", "5");
	}

	@Test
	@DisplayName("이미지는 multipart로 보낸다")
	void sendsImageAsMultipart() {
		server.expect(requestTo(IMAGE_URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Content-Type", org.hamcrest.Matchers.startsWith("multipart/form-data")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"image\"")))
			.andRespond(withSuccess("{\"placeNames\":[\"연우재\"]}", MediaType.APPLICATION_JSON));

		List<String> names = extractor("https://ai.test").fromImage(new byte[] {1, 2, 3}, "image/png");

		assertThat(names).containsExactly("연우재");
	}

	@Test
	@DisplayName("주소가 설정되지 않았으면 호출하지 않고 실패한다")
	void failsWithoutCallingWhenNotConfigured() {
		assertThatThrownBy(() -> extractor("").fromText("x")).satisfies(thrown -> assertReason(thrown, Reason.AUTH));
		server.verify();
	}

	@Test
	@DisplayName("5xx면 한 번 재시도하고 성공하면 결과를 돌려준다")
	void retriesOnceOnServerError() {
		server.expect(ExpectedCount.once(), requestTo(TEXT_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
		server.expect(ExpectedCount.once(), requestTo(TEXT_URL))
			.andRespond(withSuccess("{\"placeNames\":[\"연우재\"]}", MediaType.APPLICATION_JSON));

		assertThat(extractor("https://ai.test").fromText("x")).containsExactly("연우재");
		server.verify();
	}

	@Test
	@DisplayName("5xx가 반복되면 두 번 호출한 뒤 SERVER_ERROR로 실패한다")
	void failsAfterTwoServerErrors() {
		server.expect(ExpectedCount.times(2), requestTo(TEXT_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		assertThatThrownBy(() -> extractor("https://ai.test").fromText("x"))
			.satisfies(thrown -> assertReason(thrown, Reason.SERVER_ERROR));
		server.verify();
	}

	@Test
	@DisplayName("연결 실패가 반복되면 NETWORK로 실패한다")
	void failsOnRepeatedConnectionFailure() {
		server.expect(ExpectedCount.times(2), requestTo(TEXT_URL)).andRespond(withException(new IOException("down")));

		assertThatThrownBy(() -> extractor("https://ai.test").fromText("x"))
			.satisfies(thrown -> assertReason(thrown, Reason.NETWORK));
		server.verify();
	}

	@Test
	@DisplayName("4xx는 재시도하지 않고 원인별로 분류한다")
	void classifiesClientErrorsWithoutRetry() {
		server.expect(ExpectedCount.once(), requestTo(TEXT_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		assertThatThrownBy(() -> extractor("https://ai.test").fromText("x"))
			.satisfies(thrown -> assertReason(thrown, Reason.RATE_LIMIT));
		server.verify();
		server.reset();

		server.expect(ExpectedCount.once(), requestTo(TEXT_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
		assertThatThrownBy(() -> extractor("https://ai.test").fromText("x"))
			.satisfies(thrown -> assertReason(thrown, Reason.AUTH));
		server.verify();
		server.reset();

		server.expect(ExpectedCount.once(), requestTo(TEXT_URL)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));
		assertThatThrownBy(() -> extractor("https://ai.test").fromText("x"))
			.satisfies(thrown -> assertReason(thrown, Reason.REQUEST_REJECTED));
		server.verify();
	}

	@Test
	@DisplayName("응답에 placeNames가 없거나 깨진 JSON이면 BAD_RESPONSE다")
	void rejectsMalformedResponses() {
		server.expect(ExpectedCount.once(), requestTo(TEXT_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> extractor("https://ai.test").fromText("x"))
			.satisfies(thrown -> assertReason(thrown, Reason.BAD_RESPONSE));
		server.reset();

		server.expect(ExpectedCount.once(), requestTo(TEXT_URL)).andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> extractor("https://ai.test").fromText("x"))
			.satisfies(thrown -> assertReason(thrown, Reason.BAD_RESPONSE));
	}
}
