package com.hsu.oneclickmeet.domain.notification.controller;

import com.hsu.oneclickmeet.domain.notification.entity.DevicePlatform;
import com.hsu.oneclickmeet.domain.notification.service.DeviceTokenService;
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

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceTokenController.class)
class DeviceTokenControllerTests {

	private static final long MEMBER_ID = 7L;

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private DeviceTokenService service;

	private static RequestPostProcessor member() {
		return authentication(UsernamePasswordAuthenticationToken.authenticated(MEMBER_ID, null, List.of()));
	}

	@Test
	@DisplayName("기기 토큰을 본인 번호로 등록하고 204를 돌려준다")
	void registers() throws Exception {
		mvc.perform(post("/api/v1/devices").with(member()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"platform\":\"ANDROID\",\"token\":\"fcm-token\",\"memberId\":999}"))
			.andExpect(status().isNoContent());
		verify(service).register(MEMBER_ID, DevicePlatform.ANDROID, "fcm-token");
	}

	@Test
	@DisplayName("플랫폼이 없거나 모르는 값이고 토큰이 비었거나 길면 400이다")
	void rejectsInvalidBody() throws Exception {
		mvc.perform(post("/api/v1/devices").with(member()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\" \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.errors[*].field").value(hasItem("platform")))
			.andExpect(jsonPath("$.error.errors[*].field").value(hasItem("token")));
		mvc.perform(post("/api/v1/devices").with(member()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"platform\":\"WINDOWS\",\"token\":\"t\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/devices").with(member()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"platform\":\"IOS\",\"token\":\"" + "a".repeat(513) + "\"}"))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("해제는 본인 번호로 처리하고 204를 돌려준다")
	void unregisters() throws Exception {
		mvc.perform(post("/api/v1/devices/unregister").with(member()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"fcm-token\"}"))
			.andExpect(status().isNoContent());
		verify(service).unregister(MEMBER_ID, "fcm-token");
	}

	@Test
	@DisplayName("로그인하지 않으면 401이다")
	void rejectsUnauthenticated() throws Exception {
		mvc.perform(post("/api/v1/devices").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"platform\":\"IOS\",\"token\":\"t\"}"))
			.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/devices/unregister").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"t\"}"))
			.andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}
}
