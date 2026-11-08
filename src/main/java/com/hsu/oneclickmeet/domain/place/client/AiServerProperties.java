package com.hsu.oneclickmeet.domain.place.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * AI 서버(FastAPI) 설정. 주소(baseUrl)가 비어 있으면 AI 서버를 쓰지 않는 것으로 보고
 * 호출 없이 직접 검색으로 안내한다. 환경변수 AI_SERVER_BASE_URL로 주입한다.
 * serviceKey는 AI 서버의 AI_SERVICE_KEY와 같은 값이며 X-AI-Key 헤더로 보낸다(AI_SERVER_SERVICE_KEY).
 * 인식(P03·P04)은 OpenAI 호출을 포함해 오래 걸릴 수 있어 읽기 제한 시간 기본값을 30초로 둔다.
 * 코스 추천(R01)은 AI 파트 권장대로 읽기 제한 시간(recommendReadTimeout)을 15초로 따로 둔다.
 */
@ConfigurationProperties(prefix = "ai.server")
public record AiServerProperties(String baseUrl, String serviceKey, Duration connectTimeout, Duration readTimeout,
		Duration recommendReadTimeout) {

	public AiServerProperties {
		if (baseUrl == null) {
			baseUrl = "";
		}
		if (serviceKey == null) {
			serviceKey = "";
		}
		if (connectTimeout == null) {
			connectTimeout = Duration.ofSeconds(2);
		}
		if (readTimeout == null) {
			readTimeout = Duration.ofSeconds(30);
		}
		if (recommendReadTimeout == null) {
			recommendReadTimeout = Duration.ofSeconds(15);
		}
	}

	public boolean configured() {
		return !baseUrl.isBlank();
	}

	public boolean hasServiceKey() {
		return !serviceKey.isBlank();
	}
}
