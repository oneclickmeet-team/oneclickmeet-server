package com.hsu.oneclickmeet.domain.member.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 구글 로그인 설정. clientId는 우리 앱의 구글 클라이언트 ID(환경변수 GOOGLE_LOGIN_CLIENT_ID)이며,
 * 다른 앱을 위해 발급된 ID 토큰을 거르기 위해 반드시 필요하다. 없으면 구글 로그인은 동작하지 않는다.
 */
@ConfigurationProperties(prefix = "google.login")
public record GoogleLoginProperties(String clientId, String baseUrl, Duration connectTimeout, Duration readTimeout) {

	public GoogleLoginProperties {
		if (baseUrl == null || baseUrl.isBlank()) {
			baseUrl = "https://oauth2.googleapis.com";
		}
		if (connectTimeout == null) {
			connectTimeout = Duration.ofSeconds(2);
		}
		if (readTimeout == null) {
			readTimeout = Duration.ofSeconds(3);
		}
	}

	public boolean configured() {
		return clientId != null && !clientId.isBlank();
	}
}
