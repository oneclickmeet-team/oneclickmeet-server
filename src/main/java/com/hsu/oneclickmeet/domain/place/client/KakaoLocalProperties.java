package com.hsu.oneclickmeet.domain.place.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 카카오 로컬 API 설정. REST API 키는 환경변수로 주입하고 저장소에 두지 않는다.
 */
@ConfigurationProperties(prefix = "kakao.local")
public record KakaoLocalProperties(String restApiKey, String baseUrl, Duration connectTimeout, Duration readTimeout) {

	public KakaoLocalProperties {
		if (baseUrl == null || baseUrl.isBlank()) {
			baseUrl = "https://dapi.kakao.com";
		}
		if (connectTimeout == null) {
			connectTimeout = Duration.ofSeconds(2);
		}
		if (readTimeout == null) {
			readTimeout = Duration.ofSeconds(3);
		}
	}
}
