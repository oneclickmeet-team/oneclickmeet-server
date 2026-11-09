package com.hsu.oneclickmeet.domain.member.client;

import com.hsu.oneclickmeet.domain.member.exception.MemberErrorCode;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleIdentityVerifierTests {

	private static final String URL = "https://google.test/tokeninfo?id_token=id-token";

	private final RestClient.Builder builder = RestClient.builder().baseUrl("https://google.test");
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	private GoogleIdentityVerifier verifier(String clientId) {
		return new GoogleIdentityVerifier(builder.build(), new GoogleLoginProperties(clientId, null, null, null));
	}

	private static void assertCode(Throwable thrown, MemberErrorCode code) {
		assertThat(thrown).isInstanceOf(BusinessException.class);
		assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(code);
	}

	private static String info(String iss, String aud, String sub, String emailVerified) {
		return "{\"iss\":\"" + iss + "\",\"aud\":\"" + aud + "\",\"sub\":" + (sub == null ? "null" : "\"" + sub + "\"")
			+ ",\"email\":\"a@b.c\",\"email_verified\":\"" + emailVerified + "\",\"name\":\"승진\",\"exp\":\"9999999999\"}";
	}

	@Test
	@DisplayName("우리 클라이언트 ID에 발급된 구글 토큰이면 사용자 정보를 돌려준다")
	void verifiesIdToken() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(info("https://accounts.google.com", "client-1", "g-100", "true"), MediaType.APPLICATION_JSON));

		SocialIdentity identity = verifier("client-1").verify("id-token");

		assertThat(identity.provider().name()).isEqualTo("GOOGLE");
		assertThat(identity.providerUserId()).isEqualTo("g-100");
		assertThat(identity.nickname()).isEqualTo("승진");
		assertThat(identity.email()).isEqualTo("a@b.c");
		server.verify();
	}

	@Test
	@DisplayName("이메일 인증이 안 된 계정의 이메일은 쓰지 않는다")
	void ignoresUnverifiedEmail() {
		server.expect(requestTo(URL))
			.andRespond(withSuccess(info("accounts.google.com", "client-1", "g-100", "false"), MediaType.APPLICATION_JSON));

		assertThat(verifier("client-1").verify("id-token").email()).isNull();
	}

	@Test
	@DisplayName("대상(aud)이나 발급자(iss)가 다르거나 사용자 번호가 없으면 거절한다")
	void rejectsWrongAudienceIssuerOrMissingSubject() {
		server.expect(requestTo(URL))
			.andRespond(withSuccess(info("https://accounts.google.com", "other-client", "g-100", "true"), MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> verifier("client-1").verify("id-token"))
			.satisfies(thrown -> assertCode(thrown, MemberErrorCode.INVALID_SOCIAL_TOKEN));
		server.reset();

		server.expect(requestTo(URL))
			.andRespond(withSuccess(info("https://evil.example", "client-1", "g-100", "true"), MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> verifier("client-1").verify("id-token"))
			.satisfies(thrown -> assertCode(thrown, MemberErrorCode.INVALID_SOCIAL_TOKEN));
		server.reset();

		server.expect(requestTo(URL))
			.andRespond(withSuccess(info("https://accounts.google.com", "client-1", null, "true"), MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> verifier("client-1").verify("id-token"))
			.satisfies(thrown -> assertCode(thrown, MemberErrorCode.INVALID_SOCIAL_TOKEN));
	}

	@Test
	@DisplayName("구글이 토큰을 거절하면(400) 올바르지 않은 토큰이고, 구글 서버 오류는 잠시 사용할 수 없음이다")
	void classifiesProviderResponses() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
		assertThatThrownBy(() -> verifier("client-1").verify("id-token"))
			.satisfies(thrown -> assertCode(thrown, MemberErrorCode.INVALID_SOCIAL_TOKEN));
		server.reset();

		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		assertThatThrownBy(() -> verifier("client-1").verify("id-token"))
			.satisfies(thrown -> assertCode(thrown, MemberErrorCode.SOCIAL_LOGIN_UNAVAILABLE));
	}

	@Test
	@DisplayName("클라이언트 ID가 설정되지 않았으면 구글을 호출하지 않고 사용할 수 없음으로 처리한다")
	void unavailableWithoutClientId() {
		assertThatThrownBy(() -> verifier("").verify("id-token"))
			.satisfies(thrown -> assertCode(thrown, MemberErrorCode.SOCIAL_LOGIN_UNAVAILABLE));
		server.verify();
	}
}
