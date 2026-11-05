package com.hsu.oneclickmeet.domain.member.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hsu.oneclickmeet.domain.member.entity.SocialProvider;
import com.hsu.oneclickmeet.domain.member.exception.MemberErrorCode;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Set;

/**
 * 구글 ID 토큰 확인. 구글의 tokeninfo 엔드포인트가 서명·만료를 확인해 주며,
 * 우리는 대상(aud)이 우리 클라이언트 ID인지, 발급자(iss)가 구글인지 추가로 확인한다.
 * 응답 필드는 구글 문서를 따른 것이며 실제 호출로는 확인하지 못했다.
 */
@Slf4j
@Component
public class GoogleIdentityVerifier implements SocialIdentityVerifier {

	private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

	private final RestClient restClient;
	private final GoogleLoginProperties properties;

	public GoogleIdentityVerifier(@Qualifier("googleLoginRestClient") RestClient restClient,
			GoogleLoginProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	@Override
	public SocialProvider provider() {
		return SocialProvider.GOOGLE;
	}

	@Override
	public SocialIdentity verify(String idToken) {
		if (!properties.configured()) {
			log.error("구글 로그인 클라이언트 ID(GOOGLE_LOGIN_CLIENT_ID)가 설정되지 않았습니다.");
			throw new BusinessException(MemberErrorCode.SOCIAL_LOGIN_UNAVAILABLE);
		}
		try {
			TokenInfo info = restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/tokeninfo").queryParam("id_token", "{token}").build(idToken))
					.retrieve().body(TokenInfo.class);
			if (info == null || info.sub() == null || info.sub().isBlank()
					|| !properties.clientId().trim().equals(info.aud()) || !ISSUERS.contains(info.iss())) {
				log.warn("구글 토큰의 대상 또는 발급자가 올바르지 않습니다.");
				throw new BusinessException(MemberErrorCode.INVALID_SOCIAL_TOKEN);
			}
			String email = "true".equalsIgnoreCase(info.emailVerified()) ? info.email() : null;
			return new SocialIdentity(SocialProvider.GOOGLE, info.sub(), info.name(), email);
		} catch (HttpClientErrorException exception) {
			log.warn("구글이 토큰을 거절했습니다. status={}", exception.getStatusCode().value());
			throw new BusinessException(MemberErrorCode.INVALID_SOCIAL_TOKEN);
		} catch (RestClientException exception) {
			log.warn("구글 로그인 확인에 실패했습니다.");
			throw new BusinessException(MemberErrorCode.SOCIAL_LOGIN_UNAVAILABLE);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TokenInfo(String iss, String aud, String sub, String email,
			@JsonProperty("email_verified") String emailVerified, String name) {
	}
}
