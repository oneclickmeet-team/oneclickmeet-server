package com.hsu.oneclickmeet.domain.notification.controller;

import com.hsu.oneclickmeet.domain.activity.entity.ActivityType;
import com.hsu.oneclickmeet.domain.notification.dto.response.NotificationListResponse;
import com.hsu.oneclickmeet.domain.notification.dto.response.NotificationResponse;
import com.hsu.oneclickmeet.domain.notification.exception.NotificationErrorCode;
import com.hsu.oneclickmeet.domain.notification.service.NotificationService;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTests {

	private static final String URL = "/api/v1/notifications";
	private static final long MEMBER_ID = 7L;

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private NotificationService service;

	private static NotificationResponse response(long id, boolean read) {
		return new NotificationResponse(id, ActivityType.COURSE_SHARED, "제목", "내용", "COURSE", 5L, read,
				Instant.parse("2026-11-05T03:00:00Z"));
	}

	private static RequestPostProcessor member() {
		return authentication(UsernamePasswordAuthenticationToken.authenticated(MEMBER_ID, null, List.of()));
	}

	@Test
	@DisplayName("목록은 기본 20건으로 조회하고 읽지 않은 개수와 커서를 돌려준다")
	void listsWithDefaults() throws Exception {
		when(service.list(MEMBER_ID, null, 20))
				.thenReturn(new NotificationListResponse(List.of(response(2, false)), 3, "next", true));

		mvc.perform(get(URL).with(member()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].notificationId").value(2))
				.andExpect(jsonPath("$.data.items[0].read").value(false))
				.andExpect(jsonPath("$.data.unreadCount").value(3))
				.andExpect(jsonPath("$.data.nextCursor").value("next"));
	}

	@ParameterizedTest(name = "size={0}은 거절한다")
	@ValueSource(strings = {"0", "101", "abc"})
	@DisplayName("size가 1~100이 아니면 400이다")
	void rejectsInvalidSize(String size) throws Exception {
		mvc.perform(get(URL).param("size", size).with(member())).andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("읽음 처리는 본인 번호로 호출하고 없으면 404다")
	void marksRead() throws Exception {
		when(service.markRead(MEMBER_ID, 4L)).thenReturn(response(4, true));
		when(service.markRead(MEMBER_ID, 5L)).thenThrow(new BusinessException(NotificationErrorCode.NOT_FOUND));

		mvc.perform(patch(URL + "/4/read").with(member()).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.read").value(true));
		mvc.perform(patch(URL + "/5/read").with(member()).with(csrf()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOTIFICATION-0404"));
		verify(service).markRead(MEMBER_ID, 4L);
	}

	@ParameterizedTest(name = "id={0}은 거절한다")
	@ValueSource(strings = {"0", "-1", "abc"})
	@DisplayName("notificationId가 양의 정수가 아니면 400이다")
	void rejectsInvalidId(String id) throws Exception {
		mvc.perform(patch(URL + "/" + id + "/read").with(member()).with(csrf())).andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("로그인하지 않으면 401이고 서비스를 호출하지 않는다")
	void rejectsUnauthenticated() throws Exception {
		mvc.perform(get(URL)).andExpect(status().isUnauthorized());
		mvc.perform(patch(URL + "/1/read").with(csrf())).andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}
}
