package com.hsu.oneclickmeet.domain.member.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hsu.oneclickmeet.domain.member.entity.SocialProvider;
import com.hsu.oneclickmeet.domain.member.exception.MemberErrorCode;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 액세스 토큰 확인.
 * 1) GET /v1/user/access_token_info 로 토큰이 우리 앱(appId)에 발급된 것인지 확인하고,
 * 2) GET /v2/user/me 로 사용자 번호·닉네임·이메일을 얻는다.
 * 응답 필드는 카카오 개발 문서를 따른 것이며 실제 호출로는 확인하지 못했다.
 */
@Slf4j
@Component
public class KakaoIdentityVerifier implements SocialIdentityVerifier {

	private final RestClient restClient;
	private final KakaoLoginProperties properties;

	public KakaoIdentityVerifier(@Qualifier("kakaoLoginRestClient") RestClient restClient,
			KakaoLoginProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	@Override
	public SocialProvider provider() {
		return SocialProvider.KAKAO;
	}

	@Override
	public SocialIdentity verify(String accessToken) {
		if (!properties.configured()) {
			log.error("카카오 로그인 앱 번호(KAKAO_LOGIN_APP_ID)가 설정되지 않았습니다.");
			throw new BusinessException(MemberErrorCode.SOCIAL_LOGIN_UNAVAILABLE);
		}
		try {
			TokenInfo info = restClient.get().uri("/v1/user/access_token_info")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
					.retrieve().body(TokenInfo.class);
			if (info == null || !properties.appId().trim().equals(info.appIdValue())) {
				log.warn("다른 앱에서 발급된 카카오 토큰으로 로그인을 시도했습니다.");
				throw new BusinessException(MemberErrorCode.INVALID_SOCIAL_TOKEN);
			}
			Me me = restClient.get().uri("/v2/user/me")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
					.retrieve().body(Me.class);
			if (me == null || me.id() == null || me.id() <= 0) {
				log.warn("카카오 사용자 정보 응답에 사용자 번호가 없습니다.");
				throw new BusinessException(MemberErrorCode.SOCIAL_LOGIN_UNAVAILABLE);
			}
			String nickname = me.kakaoAccount() == null || me.kakaoAccount().profile() == null
					? null : me.kakaoAccount().profile().nickname();
			String email = me.kakaoAccount() == null ? null : me.kakaoAccount().email();
			return new SocialIdentity(SocialProvider.KAKAO, Long.toString(me.id()), nickname, email);
		} catch (HttpClientErrorException exception) {
			log.warn("카카오가 토큰을 거절했습니다. status={}", exception.getStatusCode().value());
			throw new BusinessException(MemberErrorCode.INVALID_SOCIAL_TOKEN);
		} catch (RestClientException exception) {
			log.warn("카카오 로그인 확인에 실패했습니다.");
			throw new BusinessException(MemberErrorCode.SOCIAL_LOGIN_UNAVAILABLE);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TokenInfo(@JsonProperty("app_id") Long appId, @JsonProperty("appId") Long legacyAppId) {

		String appIdValue() {
			Long value = appId != null ? appId : legacyAppId;
			return value == null ? null : Long.toString(value);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Me(Long id, @JsonProperty("kakao_account") Account kakaoAccount) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Account(Profile profile, String email) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Profile(String nickname) {
	}
}
