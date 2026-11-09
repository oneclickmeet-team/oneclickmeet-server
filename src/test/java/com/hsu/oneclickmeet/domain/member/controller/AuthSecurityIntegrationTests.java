package com.hsu.oneclickmeet.domain.member.controller;

import com.hsu.oneclickmeet.domain.member.dto.response.LoginResponse;
import com.hsu.oneclickmeet.domain.member.dto.response.MemberResponse;
import com.hsu.oneclickmeet.domain.member.dto.response.TokenResponse;
import com.hsu.oneclickmeet.domain.member.entity.SocialProvider;
import com.hsu.oneclickmeet.domain.member.service.AuthService;
import com.hsu.oneclickmeet.domain.member.service.MemberService;
import com.hsu.oneclickmeet.global.security.JwtProperties;
import com.hsu.oneclickmeet.global.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "jwt.secret=" + AuthSecurityIntegrationTests.SECRET)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityIntegrationTests {

	static final String SECRET = "integration-secret-integration-secret-12";
	private static final MemberResponse ME = new MemberResponse(7L, SocialProvider.KAKAO, "승진", null, Instant.parse("2026-11-05T00:00:00Z"));

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JwtTokenProvider tokenProvider;

	@MockitoBean
	private AuthService authService;
	@MockitoBean
	private MemberService memberService;

	private String bearer(long memberId) {
		return "Bearer " + tokenProvider.issueAccessToken(memberId).value();
	}

	@Test
	@DisplayName("토큰 없이 보호된 API를 부르면 공통 응답 형식의 401이다")
	void rejectsRequestsWithoutToken() throws Exception {
		mvc.perform(get("/api/v1/members/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.data").value(nullValue()))
			.andExpect(jsonPath("$.error.code").value("COMMON-0401"));
		mvc.perform(get("/api/v1/saved-places")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/recommendations/courses").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
		verifyNoInteractions(memberService);
	}

	@Test
	@DisplayName("올바른 접근 토큰이면 토큰의 회원 번호가 컨트롤러에 전달된다")
	void passesMemberIdFromToken() throws Exception {
		when(memberService.me(7L)).thenReturn(ME);

		mvc.perform(get("/api/v1/members/me").header("Authorization", bearer(7L)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.memberId").value(7))
			.andExpect(jsonPath("$.data.nickname").value("승진"));
		verify(memberService).me(7L);
	}

	@Test
	@DisplayName("만료·위조·종류가 다른 토큰과 다른 인증 방식은 401이다")
	void rejectsInvalidTokens() throws Exception {
		Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
		String expired = new JwtTokenProvider(new JwtProperties(SECRET, null, null, null), past).issueAccessToken(7L).value();
		String foreignKey = new JwtTokenProvider(new JwtProperties("another-secret-another-secret-another-1", null, null, null),
			Clock.systemUTC()).issueAccessToken(7L).value();
		String valid = tokenProvider.issueAccessToken(7L).value();

		mvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer " + foreignKey)).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer " + valid + "x")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/members/me").header("Authorization", "Basic dXNlcjpwYXNz")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer ")).andExpect(status().isUnauthorized());
		verifyNoInteractions(memberService);
	}

	@Test
	@DisplayName("로그인과 토큰 갱신은 토큰 없이 부를 수 있고 CSRF 토큰도 필요 없다")
	void loginAndRefreshArePublic() throws Exception {
		TokenResponse tokens = TokenResponse.bearer("access", "refresh", 1800);
		when(authService.login(eq(SocialProvider.KAKAO), eq("kakao-token")))
			.thenReturn(new LoginResponse(tokens, true, ME));
		when(authService.refresh("old-refresh")).thenReturn(tokens);

		mvc.perform(post("/api/v1/auth/login/kakao").contentType(MediaType.APPLICATION_JSON)
				.content("{\"accessToken\":\"kakao-token\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.tokens.accessToken").value("access"))
			.andExpect(jsonPath("$.data.tokens.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.data.newMember").value(true))
			.andExpect(jsonPath("$.data.member.memberId").value(7));
		mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"old-refresh\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.refreshToken").value("refresh"));
	}

	@Test
	@DisplayName("로그인 요청 값이 비었거나 너무 길면 400이다")
	void validatesLoginBodies() throws Exception {
		mvc.perform(post("/api/v1/auth/login/kakao").contentType(MediaType.APPLICATION_JSON).content("{\"accessToken\":\" \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		mvc.perform(post("/api/v1/auth/login/google").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/auth/login/google").contentType(MediaType.APPLICATION_JSON)
				.content("{\"idToken\":\"" + "a".repeat(4097) + "\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"\"}"))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(authService);
	}

	@Test
	@DisplayName("로그아웃은 로그인한 상태에서만 할 수 있고 본인 번호로 처리한다")
	void logoutRequiresToken() throws Exception {
		mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"r\"}"))
			.andExpect(status().isUnauthorized());
		verifyNoInteractions(authService);

		mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(7L)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"r\"}"))
			.andExpect(status().isNoContent());
		verify(authService).logout(7L, "r");
	}

	@Test
	@DisplayName("내 정보 수정은 본인 번호로 처리하고 닉네임이 비었거나 길면 400이다")
	void updatesOwnNickname() throws Exception {
		when(memberService.changeNickname(eq(7L), anyString())).thenReturn(ME);

		mvc.perform(patch("/api/v1/members/me").header("Authorization", bearer(7L)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nickname\":\"새 이름\"}"))
			.andExpect(status().isOk());
		verify(memberService).changeNickname(7L, "새 이름");

		mvc.perform(patch("/api/v1/members/me").header("Authorization", bearer(7L)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nickname\":\" \"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(patch("/api/v1/members/me").header("Authorization", bearer(7L)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nickname\":\"" + "가".repeat(31) + "\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(patch("/api/v1/members/me").contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"x\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("상태 확인(health)은 토큰 없이 열려 있다")
	void healthIsPublic() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}
}
