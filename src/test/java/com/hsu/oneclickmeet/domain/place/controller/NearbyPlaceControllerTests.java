package com.hsu.oneclickmeet.domain.place.controller;

import com.hsu.oneclickmeet.domain.place.dto.response.NearbyPlaceResponse;
import com.hsu.oneclickmeet.domain.place.dto.response.NearbyPlacesResponse;
import com.hsu.oneclickmeet.domain.place.dto.response.PlaceCandidateResponse;
import com.hsu.oneclickmeet.domain.place.exception.PlaceErrorCode;
import com.hsu.oneclickmeet.domain.place.service.NearbyCategory;
import com.hsu.oneclickmeet.domain.place.service.NearbyPlaceService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NearbyPlaceController.class)
class NearbyPlaceControllerTests {

	private static final long MEMBER_ID = 7L;

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private NearbyPlaceService service;

	private static RequestPostProcessor member() {
		return authentication(UsernamePasswordAuthenticationToken.authenticated(MEMBER_ID, null, List.of()));
	}

	@Test
	@DisplayName("기본 종류(CAFE)로 주변 장소를 거리와 함께 돌려준다")
	void returnsNearbyWithDefaultCategory() throws Exception {
		PlaceCandidateResponse place = new PlaceCandidateResponse("1", "카머", "주소", null, "카페", "CE7", 37.5, 127.0, null);
		when(service.nearby(MEMBER_ID, 5L, NearbyCategory.CAFE))
			.thenReturn(new NearbyPlacesResponse(5L, NearbyCategory.CAFE, List.of(new NearbyPlaceResponse(place, 120))));

		mvc.perform(get("/api/v1/saved-places/5/nearby").with(member()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.savedPlaceId").value(5))
			.andExpect(jsonPath("$.data.category").value("CAFE"))
			.andExpect(jsonPath("$.data.items[0].place.name").value("카머"))
			.andExpect(jsonPath("$.data.items[0].distanceMeters").value(120));
	}

	@Test
	@DisplayName("종류를 지정하면 그대로 전달한다")
	void passesCategory() throws Exception {
		when(service.nearby(MEMBER_ID, 5L, NearbyCategory.PARK))
			.thenReturn(new NearbyPlacesResponse(5L, NearbyCategory.PARK, List.of()));

		mvc.perform(get("/api/v1/saved-places/5/nearby").param("category", "PARK").with(member()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items").isEmpty());
		verify(service).nearby(MEMBER_ID, 5L, NearbyCategory.PARK);
	}

	@Test
	@DisplayName("없는 장소면 PLACE-0404다")
	void notFound() throws Exception {
		when(service.nearby(MEMBER_ID, 9L, NearbyCategory.CAFE)).thenThrow(new BusinessException(PlaceErrorCode.NOT_FOUND));

		mvc.perform(get("/api/v1/saved-places/9/nearby").with(member()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("PLACE-0404"));
	}

	@ParameterizedTest(name = "category={0}은 거절한다")
	@ValueSource(strings = {"BAR", "cafe1"})
	@DisplayName("지원하지 않는 종류는 400이다")
	void rejectsUnknownCategory(String category) throws Exception {
		mvc.perform(get("/api/v1/saved-places/5/nearby").param("category", category).with(member()))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@ParameterizedTest(name = "id={0}은 거절한다")
	@ValueSource(strings = {"0", "-1", "abc"})
	@DisplayName("savedPlaceId가 양의 정수가 아니면 400이다")
	void rejectsInvalidId(String id) throws Exception {
		mvc.perform(get("/api/v1/saved-places/" + id + "/nearby").with(member())).andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("로그인하지 않으면 401이다")
	void rejectsUnauthenticated() throws Exception {
		mvc.perform(get("/api/v1/saved-places/5/nearby")).andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}
}
