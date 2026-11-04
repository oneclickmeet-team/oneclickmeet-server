package com.hsu.oneclickmeet.domain.course.controller;

import com.hsu.oneclickmeet.domain.course.dto.response.CourseDetailResponse;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseListResponse;
import com.hsu.oneclickmeet.domain.course.exception.CourseErrorCode;
import com.hsu.oneclickmeet.domain.course.service.CourseReadService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
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

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseController.class)
class CourseControllerTests {

	private static final String COURSES_URL = "/api/v1/courses";
	private static final long MEMBER_ID = 7L;
	private static final long COURSE_ID = 41L;
	private static final Instant CREATED_AT = Instant.parse("2026-10-31T04:00:00Z");

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private CourseReadService courseReadService;

	@Test
	void 첫_목록은_principal과_기본_크기로_조회하고_빈_페이지를_반환한다() throws Exception {
		when(courseReadService.list(MEMBER_ID, null, 20)).thenReturn(new CourseListResponse(List.of(), null, false));

		mvc.perform(get(COURSES_URL).param("memberId", "999").with(member(MEMBER_ID)))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.items").isEmpty())
			.andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
			.andExpect(jsonPath("$.data.hasNext").value(false))
			.andExpect(jsonPath("$.error").value(nullValue()));
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 100})
	void 목록_조회에_커서와_페이지_크기를_전달한다(int size) throws Exception {
		var item = new CourseListResponse.Item(COURSE_ID, "산책 코스", 3, null, CREATED_AT);
		when(courseReadService.list(MEMBER_ID, "previous-cursor", size))
			.thenReturn(new CourseListResponse(List.of(item), "next-cursor", true));

		mvc.perform(get(COURSES_URL).param("cursor", "previous-cursor").param("size", Integer.toString(size))
			.with(member(MEMBER_ID)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].courseId").value(COURSE_ID))
			.andExpect(jsonPath("$.data.items[0].courseName").value("산책 코스"))
			.andExpect(jsonPath("$.data.items[0].placeCount").value(3))
			.andExpect(jsonPath("$.data.items[0].originSharedCourseId").value(nullValue()))
			.andExpect(jsonPath("$.data.items[0].createdAt").value(CREATED_AT.toString()))
			.andExpect(jsonPath("$.data.nextCursor").value("next-cursor"))
			.andExpect(jsonPath("$.data.hasNext").value(true));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "0", "101", "-1", "1.5", "bad"})
	void 목록_크기가_유효하지_않으면_400을_반환한다(String size) throws Exception {
		mvc.perform(get(COURSES_URL).param("size", size).with(member(MEMBER_ID)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.data").value(nullValue()))
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"))
			.andExpect(jsonPath("$.error.errors[0].field").value("size"));
		verifyNoInteractions(courseReadService);
	}

	@Test
	void 상세는_저장된_장소와_메뉴를_응답에_포함한다() throws Exception {
		var menu = new CourseDetailResponse.Menu(11L, "파스타", 24_000, true, CREATED_AT);
		var place = new CourseDetailResponse.Place(21L, 51L, "식당", null, null, null, List.of(menu), 24_000);
		when(courseReadService.detail(MEMBER_ID, COURSE_ID)).thenReturn(new CourseDetailResponse(
			COURSE_ID, "산책 코스", List.of(place), 24_000, 0, null, List.of(), CREATED_AT, CREATED_AT));

		mvc.perform(get(COURSES_URL + "/{courseId}", COURSE_ID).param("memberId", "999").with(member(MEMBER_ID)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.courseId").value(COURSE_ID))
			.andExpect(jsonPath("$.data.places[0].coursePlaceId").value(21))
			.andExpect(jsonPath("$.data.places[0].placeId").value(51))
			.andExpect(jsonPath("$.data.places[0].placeName").value("식당"))
			.andExpect(jsonPath("$.data.places[0].memo").doesNotHaveJsonPath())
			.andExpect(jsonPath("$.data.places[0].latitude").value(nullValue()))
			.andExpect(jsonPath("$.data.places[0].estimatedWalkingMinutesFromPrevious").value(nullValue()))
			.andExpect(jsonPath("$.data.places[0].menuList[0].menuId").value(11))
			.andExpect(jsonPath("$.data.places[0].menuList[0].updatedAt").value(CREATED_AT.toString()))
			.andExpect(jsonPath("$.data.places[0].selectedSubtotal").value(24_000))
			.andExpect(jsonPath("$.data.selectedTotalPrice").value(24_000))
			.andExpect(jsonPath("$.data.placesWithoutSelectedMenusCount").value(0))
			.andExpect(jsonPath("$.data.walkingTimeSource").value(nullValue()))
			.andExpect(jsonPath("$.data.warnings").isEmpty())
			.andExpect(jsonPath("$.error").value(nullValue()));
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "-1", "0x1", "1 2", "not-a-number", "9223372036854775808"})
	void 코스_ID가_유효하지_않으면_400을_반환한다(String courseId) throws Exception {
		mvc.perform(get(COURSES_URL + "/{courseId}", courseId).with(member(MEMBER_ID)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		verifyNoInteractions(courseReadService);
	}

	@Test
	void 소유한_코스가_없으면_공통_응답으로_404를_반환한다() throws Exception {
		when(courseReadService.detail(MEMBER_ID, COURSE_ID))
			.thenThrow(new BusinessException(CourseErrorCode.NOT_FOUND));

		mvc.perform(get(COURSES_URL + "/{courseId}", COURSE_ID).with(member(MEMBER_ID)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.data").value(nullValue()))
			.andExpect(jsonPath("$.error.code").value("COURSE-0404"))
			.andExpect(jsonPath("$.error.errors").doesNotHaveJsonPath());
	}

	@Test
	void 로그인_이름과_요청_값은_회원_ID를_대체할_수_없다() throws Exception {
		mvc.perform(get(COURSES_URL).param("memberId", "7").with(user("7")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("COMMON-0401"));
		verifyNoInteractions(courseReadService);
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, -1L})
	void 양수가_아닌_principal로는_상세를_조회할_수_없다(long memberId) throws Exception {
		mvc.perform(get(COURSES_URL + "/{courseId}", COURSE_ID).with(member(memberId)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("COMMON-0401"));
		verifyNoInteractions(courseReadService);
	}

	private RequestPostProcessor member(long memberId) {
		return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
	}
}
