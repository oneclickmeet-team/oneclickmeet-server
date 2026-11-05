package com.hsu.oneclickmeet.global.config;

import com.hsu.oneclickmeet.global.security.JwtAuthenticationFilter;
import com.hsu.oneclickmeet.global.security.JwtProperties;
import com.hsu.oneclickmeet.global.security.JwtTokenProvider;
import com.hsu.oneclickmeet.global.security.RestAuthenticationEntryPoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;

/**
 * 접근 제어 기본 설정: 서버는 세션을 쓰지 않고(JWT), 로그인·토큰 갱신·상태 확인을 뼄 모든 요청에 로그인을 요구한다.
 * 인증 정보의 principal은 회원 번호(Long)다.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

	@Bean
	@ConditionalOnMissingBean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider tokenProvider,
			RestAuthenticationEntryPoint entryPoint) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(requests -> requests
						.requestMatchers(HttpMethod.POST, "/api/v1/auth/login/*", "/api/v1/auth/refresh").permitAll()
						.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
				.addFilterBefore(new JwtAuthenticationFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class)
				.build();
	}
}
