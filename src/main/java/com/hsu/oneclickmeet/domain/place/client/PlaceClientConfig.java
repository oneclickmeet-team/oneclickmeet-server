package com.hsu.oneclickmeet.domain.place.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties({KakaoLocalProperties.class, AiServerProperties.class})
public class PlaceClientConfig {

	@Bean
	RestClient kakaoLocalRestClient(KakaoLocalProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		return RestClient.builder()
				.baseUrl(properties.baseUrl())
				.requestFactory(requestFactory)
				.build();
	}

	@Bean
	RestClient aiServerRestClient(AiServerProperties properties) {
		return aiRestClient(properties, properties.readTimeout());
	}

	/** 코스 추천(R01) 전용. 인식보다 짧은 읽기 제한 시간을 쓴다. */
	@Bean
	RestClient aiRecommendRestClient(AiServerProperties properties) {
		return aiRestClient(properties, properties.recommendReadTimeout());
	}

	private static RestClient aiRestClient(AiServerProperties properties, Duration readTimeout) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(readTimeout);
		RestClient.Builder builder = RestClient.builder()
				.baseUrl(properties.configured() ? properties.baseUrl() : "http://ai-server.not-configured")
				.requestFactory(requestFactory);
		if (properties.hasServiceKey()) {
			builder.defaultHeader("X-AI-Key", properties.serviceKey());
		}
		return builder.build();
	}
}
