package com.hsu.oneclickmeet.domain.recommendation.controller;

import com.hsu.oneclickmeet.domain.recommendation.dto.request.RecommendCoursesRequest;
import com.hsu.oneclickmeet.domain.recommendation.dto.request.RecommendCoursesRequest.Anchor;
import com.hsu.oneclickmeet.domain.recommendation.dto.request.RecommendCoursesRequest.AnchorType;
import com.hsu.oneclickmeet.domain.recommendation.dto.response.CourseRecommendationResponse;
import com.hsu.oneclickmeet.domain.recommendation.dto.response.CourseRecommendationResponse.RecommendedCourse;
import com.hsu.oneclickmeet.domain.recommendation.dto.response.CourseRecommendationResponse.RecommendedPlace;
import com.hsu.oneclickmeet.domain.recommendation.dto.response.CourseRecommendationResponse.Status;
import com.hsu.oneclickmeet.domain.recommendation.exception.RecommendationErrorCode;
import com.hsu.oneclickmeet.domain.recommendation.service.CourseCategory;
import com.hsu.oneclickmeet.domain.recommendation.service.CourseRecommendationService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
	private static final String REGION_BODY =
		"{\"anchor\":{\"type\":\"REGION\",\"regionId\":\"혜화\"},\"placeCount\":3,\"categorySequence\":[\"MEAL\",\"CAFE\",\"WALK\"]}";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private CourseRecommendationService service;

	private static RequestPostProcessor member() {
		return authentication(UsernamePasswordAuthenticationToken.authenticated(MEMBER_ID, null, List.of()));
	}

	private void send(String body) throws Exception {
		mvc.perform(post(URL).with(member()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@Test
	@DisplayName("본인 번호로 추천을 요청하고 코스·제목·도보 시간을 돌려준다")
	void recommends() throws Exception {
		RecommendedPlace first = new RecommendedPlace(1, "PERSONAL", false, "1", "연우재", "주소", null, "카페", "CAFE", 37.5,
			127.0, null, 11L, null);
		RecommendedPlace second = new RecommendedPlace(2, "EXTERNAL", true, "2", "카머", "주소", null, "맛집", "MEAL", 37.5,
			127.0, null, null, 4);
		when(service.recommend(eq(MEMBER_ID), any(RecommendCoursesRequest.class))).thenReturn(
			new CourseRecommendationResponse(Status.OK, null,
				List.of(new RecommendedCourse("crs_1", "혜화 카페·맛집 코스", 4, List.of(first, second)))));

		mvc.perform(post(URL).with(member()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REGION_BODY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("OK"))
			.andExpect(jsonPath("$.data.courses[0].candidateKey").value("crs_1"))
			.andExpect(jsonPath("$.data.courses[0].title").value("혜화 카페·맛집 코스"))
			.andExpect(jsonPath("$.data.courses[0].totalWalkMinutes").value(4))
			.andExpect(jsonPath("$.data.courses[0].places[0].savedPlaceId").value(11))
			.andExpect(jsonPath("$.data.courses[0].places[0].source").value("PERSONAL"))
			.andExpect(jsonPath("$.data.courses[0].places[1].anchor").value(true))
			.andExpect(jsonPath("$.data.courses[0].places[1].categoryCode").value("MEAL"))
			.andExpect(jsonPath("$.data.courses[0].places[1].walkMinutesFromPrevious").value(4));
		verify(service).recommend(MEMBER_ID, new RecommendCoursesRequest(new Anchor(AnchorType.REGION, "혜화", null), 3,
			List.of(CourseCategory.MEAL, CourseCategory.CAFE, CourseCategory.WALK), null, null));
	}

	@Test
	@DisplayName("장소 기준 요청의 필수 장소 위치와 제외 키를 서비스에 그대로 넘긴다")
	void passesPlaceAnchor() throws Exception {
		when(service.recommend(eq(MEMBER_ID), any())).thenReturn(CourseRecommendationResponse.notEnough("NO_MORE_COURSES"));

		send("{\"anchor\":{\"type\":\"PLACE\",\"placeId\":41},\"placeCount\":4,"
			+ "\"categorySequence\":[\"MEAL\",\"CAFE\",\"ACTIVITY\",\"BAR\"],\"requiredPlaceOrder\":2,"
			+ "\"excludeCandidateKeys\":[\"crs_a\"]}");

		verify(service).recommend(MEMBER_ID, new RecommendCoursesRequest(new Anchor(AnchorType.PLACE, null, 41L), 4,
			List.of(CourseCategory.MEAL, CourseCategory.CAFE, CourseCategory.ACTIVITY, CourseCategory.BAR), 2,
			List.of("crs_a")));
	}

	@Test
	@DisplayName("후보가 부족하면 200과 NOT_ENOUGH_CANDIDATES, 이유를 돌려준다")
	void notEnough() throws Exception {
		when(service.recommend(eq(MEMBER_ID), any())).thenReturn(CourseRecommendationResponse.notEnough("INSUFFICIENT_PLACES"));

		mvc.perform(post(URL).with(member()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REGION_BODY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("NOT_ENOUGH_CANDIDATES"))
			.andExpect(jsonPath("$.data.reason").value("INSUFFICIENT_PLACES"))
			.andExpect(jsonPath("$.data.courses").isEmpty());
	}

	@Test
	@DisplayName("지원하지 않는 지역은 400이다")
	void unsupportedArea() throws Exception {
		when(service.recommend(eq(MEMBER_ID), any())).thenThrow(new BusinessException(RecommendationErrorCode.UNSUPPORTED_AREA));

		mvc.perform(post(URL).with(member()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REGION_BODY))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("RECOMMENDATION-0401"));
	}

	@Test
	@DisplayName("AI 서버를 쓸 수 없으면 502다")
	void upstreamFailure() throws Exception {
		when(service.recommend(eq(MEMBER_ID), any())).thenThrow(new BusinessException(RecommendationErrorCode.UPSTREAM_FAILURE));

		mvc.perform(post(URL).with(member()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REGION_BODY))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.error.code").value("RECOMMENDATION-0502"));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@ValueSource(strings = {
		"{}",
		"{\"placeCount\":3,\"categorySequence\":[\"MEAL\",\"CAFE\",\"WALK\"]}",
		"{\"anchor\":{\"type\":\"REGION\",\"regionId\":\"혜화\"},\"placeCount\":2,\"categorySequence\":[\"MEAL\",\"CAFE\"]}",
		"{\"anchor\":{\"type\":\"REGION\",\"regionId\":\"혜화\"},\"placeCount\":6,\"categorySequence\":[\"MEAL\",\"MEAL\",\"MEAL\",\"MEAL\",\"MEAL\",\"MEAL\"]}",
		"{\"anchor\":{\"type\":\"REGION\",\"regionId\":\"혜화\"},\"placeCount\":3,\"categorySequence\":[\"MEAL\",\"CAFE\"]}",
		"{\"anchor\":{\"type\":\"REGION\",\"regionId\":\"혜화\"},\"placeCount\":3,\"categorySequence\":[]}",
		"{\"anchor\":{\"type\":\"REGION\",\"regionId\":\"혜화\"},\"placeCount\":3,\"categorySequence\":[\"MEAL\",\"CAFE\",\"HOTEL\"]}",
		"{\"anchor\":{\"type\":\"REGION\",\"regionId\":\"혜화\"},\"placeCount\":3,\"categorySequence\":[\"MEAL\",\"CAFE\",null]}",
		"{\"anchor\":{\"type\":\"REGION\",\"regionId\":\"혜화\"},\"placeCount\":3,\"categorySequence\":[\"MEAL\",\"CAFE\",\"WALK\"],\"requiredPlaceOrder\":1}",
		"{\"anchor\":{\"type\":\"PLACE\",\"placeId\":5},\"placeCount\":3,\"categorySequence\":[\"MEAL\",\"CAFE\",\"WALK\"]}",
		"{\"anchor\":{\"type\":\"PLACE\",\"placeId\":5},\"placeCount\":3,\"categorySequence\":[\"MEAL\",\"CAFE\",\"WALK\"],\"requiredPlaceOrder\":4}",
		"{\"anchor\":{\"type\":\"PLACE\",\"placeId\":5},\"placeCount\":3,\"categorySequence\":[\"MEAL\",\"CAFE\",\"WALK\"],\"requiredPlaceOrder\":0}",
		"{\"anchor\":{\"type\":\"PLACE\",\"placeId\":-1},\"placeCount\":3,\"categorySequence\":[\"MEAL\",\"CAFE\",\"WALK\"],\"requiredPlaceOrder\":1}",
		"{\"anchor\":{\"type\":\"PLACE\",\"regionId\":\"혜화\",\"placeId\":5},\"placeCount\":3,\"categorySequence\":[\"MEAL\",\"CAFE\",\"WALK\"],\"requiredPlaceOrder\":1}",
		"{\"anchor\":{\"type\":\"REGION\"},\"placeCount\":3,\"categorySequence\":[\"MEAL\",\"CAFE\",\"WALK\"]}",
		"{\"anchor\":{\"regionId\":\"혜화\"},\"placeCount\":3,\"categorySequence\":[\"MEAL\",\"CAFE\",\"WALK\"]}",
		"{\"anchor\":{\"type\":\"SHARED\",\"placeId\":5},\"placeCount\":3,\"categorySequence\":[\"MEAL\",\"CAFE\",\"WALK\"]}"
	})
	@DisplayName("잘못된 요청 본문은 400이고 서비스를 호출하지 않는다")
	void rejectsInvalidBody(String body) throws Exception {
		mvc.perform(post(URL).with(member()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("로그인하지 않으면 401이다")
	void rejectsUnauthenticated() throws Exception {
		mvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REGION_BODY))
			.andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}
}
