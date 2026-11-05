package com.hsu.oneclickmeet.domain.activity.controller;

import com.hsu.oneclickmeet.domain.activity.dto.response.ActivityListResponse;
import com.hsu.oneclickmeet.domain.activity.dto.response.ActivityResponse;
import com.hsu.oneclickmeet.domain.activity.entity.ActivityType;
import com.hsu.oneclickmeet.domain.activity.service.ActivityService;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityController.class)
class ActivityControllerTests {

	private static final String URL = "/api/v1/activities";
	private static final long MEMBER_ID = 7L;

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ActivityService service;

	private static RequestPostProcessor member() {
		return authentication(UsernamePasswordAuthenticationToken.authenticated(MEMBER_ID, null, List.of()));
	}

	@Test
	@DisplayName("활동 기록을 본인 번호로 기본 20건 조회한다")
	void listsOwnActivities() throws Exception {
		when(service.list(MEMBER_ID, null, 20)).thenReturn(new ActivityListResponse(
				List.of(new ActivityResponse(3, ActivityType.COURSE_DELETED, "COURSE", 5L, "삭제",
						Instant.parse("2026-11-05T03:00:00Z"))), "next", true));

		mvc.perform(get(URL).with(member()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].activityId").value(3))
				.andExpect(jsonPath("$.data.items[0].type").value("COURSE_DELETED"))
				.andExpect(jsonPath("$.data.nextCursor").value("next"))
				.andExpect(jsonPath("$.data.hasNext").value(true));
	}

	@Test
	@DisplayName("커서와 size를 서비스로 전달한다")
	void passesCursorAndSize() throws Exception {
		when(service.list(MEMBER_ID, "abc", 5)).thenReturn(new ActivityListResponse(List.of(), null, false));

		mvc.perform(get(URL).param("cursor", "abc").param("size", "5").with(member())).andExpect(status().isOk());
		verify(service).list(MEMBER_ID, "abc", 5);
	}

	@ParameterizedTest(name = "size={0}은 거절한다")
	@ValueSource(strings = {"0", "101", "abc"})
	@DisplayName("size가 1~100이 아니면 400이다")
	void rejectsInvalidSize(String size) throws Exception {
		mvc.perform(get(URL).param("size", size).with(member())).andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("로그인하지 않으면 401이다")
	void rejectsUnauthenticated() throws Exception {
		mvc.perform(get(URL)).andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}
}
