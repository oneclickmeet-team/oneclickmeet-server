package com.hsu.oneclickmeet.domain.member.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 카카오 로그인 설정. appId는 우리 앱의 카카오 앱 번호(환경변수 KAKAO_LOGIN_APP_ID)이며,
 * 다른 앱이 받은 토큰으로 로그인하는 것을 막기 위해 반드시 필요하다. 없으면 카카오 로그인은 동작하지 않는다.
 */
@ConfigurationProperties(prefix = "kakao.login")
public record KakaoLoginProperties(String appId, String baseUrl, Duration connectTimeout, Duration readTimeout) {

	public KakaoLoginProperties {
		if (baseUrl == null || baseUrl.isBlank()) {
			baseUrl = "https://kapi.kakao.com";
		}
		if (connectTimeout == null) {
			connectTimeout = Duration.ofSeconds(2);
		}
		if (readTimeout == null) {
			readTimeout = Duration.ofSeconds(3);
		}
	}

	public boolean configured() {
		return appId != null && !appId.isBlank();
	}
}
