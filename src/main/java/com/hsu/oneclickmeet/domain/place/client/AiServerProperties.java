package com.hsu.oneclickmeet.domain.place.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * AI 서버(FastAPI) 설정. 주소(baseUrl)가 비어 있으면 AI 서버를 쓰지 않는 것으로 보고
 * 호출 없이 직접 검색으로 안내한다. 환경변수 AI_SERVER_BASE_URL로 주입한다.
 */
@ConfigurationProperties(prefix = "ai.server")
public record AiServerProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {

	public AiServerProperties {
		if (baseUrl == null) {
			baseUrl = "";
		}
		if (connectTimeout == null) {
			connectTimeout = Duration.ofSeconds(2);
		}
		if (readTimeout == null) {
			readTimeout = Duration.ofSeconds(10);
		}
	}

	public boolean configured() {
		return !baseUrl.isBlank();
	}
}
