package com.hsu.oneclickmeet.domain.place.controller;

import com.hsu.oneclickmeet.domain.place.client.PlaceCandidate;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchClient;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchCommand;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchResult;
import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException;
import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException.Reason;
import com.hsu.oneclickmeet.domain.place.service.PlaceSearchService;
import com.hsu.oneclickmeet.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class PlaceSearchControllerTests {

	private static final PlaceCandidate CANDIDATE = new PlaceCandidate(
		"123", "예시 카페", "서울 성북구 삼선동 1", null, "음식점 > 카페", "CE7", 37.5822, 127.0105, "http://place.map.kakao.com/123");

	private final FakeClient client = new FakeClient();
	private LocalValidatorFactoryBean validator;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mvc = standaloneSetup(new PlaceSearchController(new PlaceSearchService(client)))
			.setControllerAdvice(new GlobalExceptionHandler())
			.setValidator(validator)
			.build();
	}

	@AfterEach
	void closeValidator() {
		validator.close();
	}

	@Test
	@DisplayName("검색 결과를 공통 응답(success/data/error)으로 돌려준다")
	void returnsCandidatesInCommonResponse() throws Exception {
		client.result = new PlaceSearchResult(List.of(CANDIDATE), 1, true);

		mvc.perform(get("/api/v1/places/search").param("query", "한성대입구역 카페"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.error").doesNotExist())
			.andExpect(jsonPath("$.data.page").value(1))
			.andExpect(jsonPath("$.data.hasNext").value(true))
			.andExpect(jsonPath("$.data.candidates[0].providerPlaceId").value("123"))
			.andExpect(jsonPath("$.data.candidates[0].name").value("예시 카페"))
			.andExpect(jsonPath("$.data.candidates[0].roadAddress").doesNotExist())
			.andExpect(jsonPath("$.data.candidates[0].lat").value(37.5822))
			.andExpect(jsonPath("$.data.candidates[0].lng").value(127.0105));
	}

	@Test
	@DisplayName("page와 size를 생략하면 기본값(1쪽, 10건)으로 검색한다")
	void usesDefaultPageAndSize() throws Exception {
		mvc.perform(get("/api/v1/places/search").param("query", "카페")).andExpect(status().isOk());

		assertThat(client.commands).singleElement().satisfies(command -> {
			assertThat(command.page()).isEqualTo(1);
			assertThat(command.size()).isEqualTo(10);
		});
	}

	@Test
	@DisplayName("page, size를 서비스로 전달한다")
	void passesPageAndSize() throws Exception {
		mvc.perform(get("/api/v1/places/search").param("query", "카페").param("page", "2").param("size", "5"))
			.andExpect(status().isOk());

		assertThat(client.commands).singleElement().satisfies(command -> {
			assertThat(command.page()).isEqualTo(2);
			assertThat(command.size()).isEqualTo(5);
		});
	}

	@Test
	@DisplayName("결과 상한을 넘는 page는 카카오를 호출하지 않고 빈 결과를 200으로 돌려준다")
	void returnsEmptyForPageBeyondLimit() throws Exception {
		mvc.perform(get("/api/v1/places/search").param("query", "카페").param("page", "6").param("size", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.candidates").isEmpty())
			.andExpect(jsonPath("$.data.hasNext").value(false));

		assertThat(client.commands).isEmpty();
	}

	@Test
	@DisplayName("검색어가 없거나 공백이면 400과 필드별 오류를 돌려준다")
	void rejectsMissingOrBlankQuery() throws Exception {
		for (var request : List.of(get("/api/v1/places/search"), get("/api/v1/places/search").param("query", "   "))) {
			mvc.perform(request)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.data").doesNotExist())
				.andExpect(jsonPath("$.error.code").value("COMMON-0400"))
				.andExpect(jsonPath("$.error.errors[*].field").value(hasItem("query")));
		}
		assertThat(client.commands).isEmpty();
	}

	@Test
	@DisplayName("size가 15를 넘거나 page가 1 미만이면 400이다")
	void rejectsOutOfRangePageAndSize() throws Exception {
		mvc.perform(get("/api/v1/places/search").param("query", "카페").param("size", "16"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.errors[*].field").value(hasItem("size")));
		mvc.perform(get("/api/v1/places/search").param("query", "카페").param("page", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.errors[*].field").value(hasItem("page")));
	}

	@Test
	@DisplayName("lat·lng를 보내도 무시하고 카카오에는 위치를 전달하지 않는다")
	void ignoresLocationParameters() throws Exception {
		mvc.perform(get("/api/v1/places/search").param("query", "카페").param("lat", "37.58").param("lng", "127.01"))
			.andExpect(status().isOk());

		assertThat(client.commands).singleElement().satisfies(command -> {
			assertThat(command.lat()).isNull();
			assertThat(command.lng()).isNull();
		});
	}

	@Test
	@DisplayName("숫자가 아닌 page는 400이다")
	void rejectsNonNumericPage() throws Exception {
		mvc.perform(get("/api/v1/places/search").param("query", "카페").param("page", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
	}

	@Test
	@DisplayName("카카오 호출이 실패하면 원인을 노출하지 않고 502(PLACE-0502)로 응답한다")
	void returnsBadGatewayWhenUpstreamFails() throws Exception {
		for (Reason reason : Reason.values()) {
			client.failure = new PlaceUpstreamException(reason, "내부 상세 " + reason);

			mvc.perform(get("/api/v1/places/search").param("query", "카페"))
				.andExpect(status().isBadGateway())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.data").doesNotExist())
				.andExpect(jsonPath("$.error.code").value("PLACE-0502"))
				.andExpect(jsonPath("$.error.message").value("장소 정보를 가져오지 못했습니다. 잠시 후 다시 시도해 주세요."))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("내부 상세"))));
		}
	}

	private static class FakeClient implements PlaceSearchClient {

		private final List<PlaceSearchCommand> commands = new ArrayList<>();
		private PlaceSearchResult result = new PlaceSearchResult(List.of(), 1, false);
		private RuntimeException failure;

		@Override
		public PlaceSearchResult search(PlaceSearchCommand command) {
			commands.add(command);
			if (failure != null) {
				throw failure;
			}
			return result;
		}
	}
}
