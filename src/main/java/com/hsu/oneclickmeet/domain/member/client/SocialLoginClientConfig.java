package com.hsu.oneclickmeet.domain.member.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties({KakaoLoginProperties.class, GoogleLoginProperties.class})
public class SocialLoginClientConfig {

	@Bean
	RestClient kakaoLoginRestClient(KakaoLoginProperties properties) {
		return restClient(properties.baseUrl(), properties.connectTimeout(), properties.readTimeout());
	}

	@Bean
	RestClient googleLoginRestClient(GoogleLoginProperties properties) {
		return restClient(properties.baseUrl(), properties.connectTimeout(), properties.readTimeout());
	}

	private static RestClient restClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(readTimeout);
		return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
	}
}
