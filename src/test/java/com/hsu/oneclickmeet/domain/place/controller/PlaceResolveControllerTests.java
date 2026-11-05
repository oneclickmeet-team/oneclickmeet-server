package com.hsu.oneclickmeet.domain.place.controller;

import com.hsu.oneclickmeet.domain.place.client.PlaceCandidate;
import com.hsu.oneclickmeet.domain.place.client.PlaceNameExtractor;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchClient;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchResult;
import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException;
import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException.Reason;
import com.hsu.oneclickmeet.domain.place.service.PlaceResolveService;
import com.hsu.oneclickmeet.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class PlaceResolveControllerTests {

	private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

	private List<String> names = List.of("연우재");
	private RuntimeException extractorFailure;
	private RuntimeException searchFailure;
	private LocalValidatorFactoryBean validator;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		PlaceNameExtractor extractor = new PlaceNameExtractor() {
			@Override
			public List<String> fromText(String sharedText) {
				if (extractorFailure != null) {
					throw extractorFailure;
				}
				return names;
			}

			@Override
			public List<String> fromImage(byte[] image, String contentType) {
				return fromText("");
			}
		};
		PlaceSearchClient search = command -> {
			if (searchFailure != null) {
				throw searchFailure;
			}
			return new PlaceSearchResult(List.of(new PlaceCandidate("1", command.query(), "서울 성북구 성북동1가 61", null,
				"음식점 > 카페", "CE7", 37.5894, 127.0044, "http://place.map.kakao.com/1")), 1, false);
		};
		validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mvc = standaloneSetup(new PlaceResolveController(new PlaceResolveService(extractor, search)))
			.setControllerAdvice(new GlobalExceptionHandler())
			.setValidator(validator)
			.build();
	}

	@AfterEach
	void closeValidator() {
		validator.close();
	}

	@Test
	@DisplayName("링크 텍스트로 후보를 찾으면 CANDIDATES_FOUND와 후보를 돌려준다")
	void resolvesLink() throws Exception {
		mvc.perform(post("/api/v1/places/resolve/link").contentType(MediaType.APPLICATION_JSON)
				.content("{\"sharedText\":\"[네이버지도] 연우재 https://naver.me/abc\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.status").value("CANDIDATES_FOUND"))
			.andExpect(jsonPath("$.data.candidates[0].name").value("연우재"))
			.andExpect(jsonPath("$.data.candidates[0].providerPlaceId").value("1"));
	}

	@Test
	@DisplayName("이름을 못 찾으면 NEEDS_SEARCH를 200으로 돌려준다")
	void needsSearchWhenNoNames() throws Exception {
		names = List.of();

		mvc.perform(post("/api/v1/places/resolve/link").contentType(MediaType.APPLICATION_JSON)
				.content("{\"sharedText\":\"https://naver.me/abc\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("NEEDS_SEARCH"))
			.andExpect(jsonPath("$.data.candidates").isEmpty());
	}

	@Test
	@DisplayName("sharedText가 비었거나 너무 길면 400이다")
	void rejectsInvalidSharedText() throws Exception {
		mvc.perform(post("/api/v1/places/resolve/link").contentType(MediaType.APPLICATION_JSON).content("{\"sharedText\":\"  \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.errors[*].field").value(hasItem("sharedText")));
		mvc.perform(post("/api/v1/places/resolve/link").contentType(MediaType.APPLICATION_JSON)
				.content("{\"sharedText\":\"" + "a".repeat(2001) + "\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("이미지로 후보를 찾는다")
	void resolvesImage() throws Exception {
		mvc.perform(multipart("/api/v1/places/resolve/image")
				.file(new MockMultipartFile("image", "shot.png", "image/png", PNG)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CANDIDATES_FOUND"));
	}

	@Test
	@DisplayName("이미지가 없거나 형식이 맞지 않으면 400이다")
	void rejectsMissingOrInvalidImage() throws Exception {
		mvc.perform(multipart("/api/v1/places/resolve/image")).andExpect(status().isBadRequest());
		mvc.perform(multipart("/api/v1/places/resolve/image")
				.file(new MockMultipartFile("image", "a.txt", "text/plain", "hello".getBytes())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
	}

	@Test
	@DisplayName("AI 서버 오류는 숨기고 NEEDS_SEARCH로, 카카오 오류는 502로 응답한다")
	void hidesAiFailureButReportsKakaoFailure() throws Exception {
		extractorFailure = new PlaceUpstreamException(Reason.NETWORK, "내부 상세");
		mvc.perform(multipart("/api/v1/places/resolve/image")
				.file(new MockMultipartFile("image", "shot.png", "image/png", PNG)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("NEEDS_SEARCH"));

		extractorFailure = null;
		searchFailure = new PlaceUpstreamException(Reason.RATE_LIMIT, "내부 상세");
		mvc.perform(post("/api/v1/places/resolve/link").contentType(MediaType.APPLICATION_JSON)
				.content("{\"sharedText\":\"연우재\"}"))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.error.code").value("PLACE-0502"));
	}
}
