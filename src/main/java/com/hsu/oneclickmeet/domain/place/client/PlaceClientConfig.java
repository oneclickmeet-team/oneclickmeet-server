package com.hsu.oneclickmeet.domain.place.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

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
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		RestClient.Builder builder = RestClient.builder()
				.baseUrl(properties.configured() ? properties.baseUrl() : "http://ai-server.not-configured")
				.requestFactory(requestFactory);
		if (properties.hasServiceKey()) {
			builder.defaultHeader("X-AI-Key", properties.serviceKey());
		}
		return builder.build();
	}
}
