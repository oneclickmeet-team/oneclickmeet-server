package com.hsu.oneclickmeet.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 서버 토큰(JWT) 설정. 서명 비밀키는 환경변수 JWT_SECRET으로 주입하고 저장소에 두지 않는다.
 * 비밀키가 없으면 실행할 때마다 임시 키를 만들어 쓰므로 재시작하면 발급한 토큰이 모두 무효가 된다.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {

	public JwtProperties {
		if (issuer == null || issuer.isBlank()) {
			issuer = "oneclickmeet";
		}
		if (accessTokenTtl == null) {
			accessTokenTtl = Duration.ofMinutes(30);
		}
		if (refreshTokenTtl == null) {
			refreshTokenTtl = Duration.ofDays(14);
		}
	}
}
