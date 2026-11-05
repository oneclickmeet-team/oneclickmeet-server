package com.hsu.oneclickmeet.domain.member.client;

import com.hsu.oneclickmeet.domain.member.exception.MemberErrorCode;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoIdentityVerifierTests {

	private static final String INFO_URL = "https://kapi.test/v1/user/access_token_info";
	private static final String ME_URL = "https://kapi.test/v2/user/me";

	private final RestClient.Builder builder = RestClient.builder().baseUrl("https://kapi.test");
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	private KakaoIdentityVerifier verifier(String appId) {
		return new KakaoIdentityVerifier(builder.build(), new KakaoLoginProperties(appId, null, null, null));
	}

	private static void assertCode(Throwable thrown, MemberErrorCode code) {
		assertThat(thrown).isInstanceOf(BusinessException.class);
		assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(code);
	}

	@Test
	@DisplayName("우리 앱에 발급된 토큰이면 사용자 번호·닉네임·이메일을 돌려주고 토큰을 Bearer로 보낸다")
	void verifiesTokenAndReadsProfile() {
		server.expect(requestTo(INFO_URL)).andExpect(method(HttpMethod.GET))
			.andExpect(header("Authorization", "Bearer kakao-token"))
			.andRespond(withSuccess("{\"id\":1234,\"expires_in\":3000,\"app_id\":777}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(ME_URL)).andExpect(method(HttpMethod.GET))
			.andExpect(header("Authorization", "Bearer kakao-token"))
			.andRespond(withSuccess("""
				{"id":1234,"connected_at":"2026-01-01T00:00:00Z",
				 "kakao_account":{"profile":{"nickname":"승진"},"email":"a@b.c","has_email":true}}
				""", MediaType.APPLICATION_JSON));

		SocialIdentity identity = verifier("777").verify("kakao-token");

		assertThat(identity.provider().name()).isEqualTo("KAKAO");
		assertThat(identity.providerUserId()).isEqualTo("1234");
		assertThat(identity.nickname()).isEqualTo("승진");
		assertThat(identity.email()).isEqualTo("a@b.c");
		server.verify();
	}

	@Test
	@DisplayName("프로필 동의가 없어 정보가 비어 있어도 사용자 번호로 로그인할 수 있다")
	void allowsMissingProfile() {
		server.expect(requestTo(INFO_URL)).andRespond(withSuccess("{\"app_id\":777}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(ME_URL)).andRespond(withSuccess("{\"id\":99}", MediaType.APPLICATION_JSON));

		SocialIdentity identity = verifier("777").verify("t");

		assertThat(identity.providerUserId()).isEqualTo("99");
		assertThat(identity.nickname()).isNull();
		assertThat(identity.email()).isNull();
	}

	@Test
	@DisplayName("다른 앱에서 발급된 토큰은 사용자 정보를 읽지 않고 거절한다")
	void rejectsTokenFromAnotherApp() {
		server.expect(ExpectedCount.once(), requestTo(INFO_URL))
			.andRespond(withSuccess("{\"app_id\":555}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> verifier("777").verify("t"))
			.satisfies(thrown -> assertCode(thrown, MemberErrorCode.INVALID_SOCIAL_TOKEN));
		server.verify();
	}

	@Test
	@DisplayName("카카오가 토큰을 거절하면(401) 올바르지 않은 토큰이다")
	void rejectsTokenRefusedByKakao() {
		server.expect(requestTo(INFO_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertThatThrownBy(() -> verifier("777").verify("t"))
			.satisfies(thrown -> assertCode(thrown, MemberErrorCode.INVALID_SOCIAL_TOKEN));
	}

	@Test
	@DisplayName("카카오 서버 오류나 사용자 번호가 없는 응답은 잠시 사용할 수 없음으로 처리한다")
	void treatsUpstreamProblemsAsUnavailable() {
		server.expect(requestTo(INFO_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
		assertThatThrownBy(() -> verifier("777").verify("t"))
			.satisfies(thrown -> assertCode(thrown, MemberErrorCode.SOCIAL_LOGIN_UNAVAILABLE));
		server.reset();

		server.expect(requestTo(INFO_URL)).andRespond(withSuccess("{\"app_id\":777}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(ME_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> verifier("777").verify("t"))
			.satisfies(thrown -> assertCode(thrown, MemberErrorCode.SOCIAL_LOGIN_UNAVAILABLE));
	}

	@Test
	@DisplayName("앱 번호가 설정되지 않았으면 카카오를 호출하지 않고 사용할 수 없음으로 처리한다")
	void unavailableWithoutAppId() {
		assertThatThrownBy(() -> verifier("").verify("t"))
			.satisfies(thrown -> assertCode(thrown, MemberErrorCode.SOCIAL_LOGIN_UNAVAILABLE));
		assertThatThrownBy(() -> verifier(null).verify("t"))
			.satisfies(thrown -> assertCode(thrown, MemberErrorCode.SOCIAL_LOGIN_UNAVAILABLE));
		server.verify();
	}
}
