package com.hsu.oneclickmeet.domain.recommendation.controller;

import com.hsu.oneclickmeet.domain.recommendation.dto.request.RecommendCoursesRequest;
import com.hsu.oneclickmeet.domain.recommendation.dto.response.CourseRecommendationResponse;
import com.hsu.oneclickmeet.domain.recommendation.dto.response.CourseRecommendationResponse.RecommendedCourse;
import com.hsu.oneclickmeet.domain.recommendation.dto.response.CourseRecommendationResponse.RecommendedPlace;
import com.hsu.oneclickmeet.domain.recommendation.dto.response.CourseRecommendationResponse.Status;
import com.hsu.oneclickmeet.domain.recommendation.exception.RecommendationErrorCode;
import com.hsu.oneclickmeet.domain.recommendation.service.CourseRecommendationService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseRecommendationController.class)
class CourseRecommendationControllerTests {

	private static final String URL = "/api/v1/recommendations/courses";
	private static final long MEMBER_ID = 7L;

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private CourseRecommendationService service;

	private static RequestPostProcessor member() {
		return authentication(UsernamePasswordAuthenticationToken.authenticated(MEMBER_ID, null, List.of()));
	}

	@Test
	@DisplayName("본인 번호로 추천을 요청하고 코스와 도보 시간을 돌려준다")
	void recommends() throws Exception {
		RecommendedPlace first = new RecommendedPlace(1, "1", "연우재", "주소", null, "카페", "CE7", 37.5, 127.0, null, 11L, null);
		RecommendedPlace second = new RecommendedPlace(2, "2", "카머", "주소", null, "카페", "CE7", 37.5, 127.0, null, null, 4);
		when(service.recommend(eq(MEMBER_ID), any(RecommendCoursesRequest.class))).thenReturn(new CourseRecommendationResponse(
			Status.OK, List.of(new RecommendedCourse("key1", 4, List.of(first, second)))));

		mvc.perform(post(URL).with(member()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"area\":\"혜화\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("OK"))
			.andExpect(jsonPath("$.data.courses[0].candidateKey").value("key1"))
			.andExpect(jsonPath("$.data.courses[0].totalWalkMinutes").value(4))
			.andExpect(jsonPath("$.data.courses[0].places[0].savedPlaceId").value(11))
			.andExpect(jsonPath("$.data.courses[0].places[1].walkMinutesFromPrevious").value(4));
		verify(service).recommend(MEMBER_ID, new RecommendCoursesRequest(null, "혜화"));
	}

	@Test
	@DisplayName("후보가 부족하면 200과 NOT_ENOUGH_CANDIDATES다")
	void notEnough() throws Exception {
		when(service.recommend(eq(MEMBER_ID), any())).thenReturn(CourseRecommendationResponse.notEnough());

		mvc.perform(post(URL).with(member()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"area\":\"잠실\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("NOT_ENOUGH_CANDIDATES"))
			.andExpect(jsonPath("$.data.courses").isEmpty());
	}

	@Test
	@DisplayName("지원하지 않는 지역이나 시작점 오류는 400이다")
	void startErrors() throws Exception {
		when(service.recommend(eq(MEMBER_ID), any())).thenThrow(new BusinessException(RecommendationErrorCode.UNSUPPORTED_AREA));

		mvc.perform(post(URL).with(member()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"area\":\"강남\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("RECOMMENDATION-0401"));
	}

	@Test
	@DisplayName("anchorSavedPlaceId가 양수가 아니거나 area가 너무 길면 400이고 서비스를 호출하지 않는다")
	void rejectsInvalidBody() throws Exception {
		mvc.perform(post(URL).with(member()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"anchorSavedPlaceId\":-1}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		mvc.perform(post(URL).with(member()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"area\":\"" + "가".repeat(31) + "\"}"))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("로그인하지 않으면 401이다")
	void rejectsUnauthenticated() throws Exception {
		mvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"area\":\"혜화\"}"))
			.andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}
}
