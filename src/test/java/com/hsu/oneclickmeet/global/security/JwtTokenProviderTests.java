package com.hsu.oneclickmeet.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTests {

	private static final String SECRET = "test-secret-test-secret-test-secret-1234";
	private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");

	private static Clock at(Instant instant) {
		return Clock.fixed(instant, ZoneOffset.UTC);
	}

	private static JwtTokenProvider provider(String secret, String issuer, Clock clock) {
		return new JwtTokenProvider(new JwtProperties(secret, issuer, Duration.ofMinutes(30), Duration.ofDays(14)), clock);
	}

	@Test
	@DisplayName("발급한 접근 토큰에서 회원 번호를 읽고 유효 시간(초)을 돌려준다")
	void issuesAndParsesAccessToken() {
		JwtTokenProvider provider = provider(SECRET, "issuer", at(NOW));

		var issued = provider.issueAccessToken(42L);

		assertThat(issued.expiresInSeconds()).isEqualTo(1800);
		assertThat(provider.parseAccessToken(issued.value())).contains(42L);
	}

	@Test
	@DisplayName("만료된 토큰은 거절한다")
	void rejectsExpiredToken() {
		String token = provider(SECRET, "issuer", at(NOW)).issueAccessToken(1L).value();

		assertThat(provider(SECRET, "issuer", at(NOW.plus(Duration.ofMinutes(29)))).parseAccessToken(token)).contains(1L);
		assertThat(provider(SECRET, "issuer", at(NOW.plus(Duration.ofMinutes(31)))).parseAccessToken(token)).isEmpty();
	}

	@Test
	@DisplayName("서명이 다르거나 내용이 바뀔 토큰, 발급자가 다른 토큰은 거절한다")
	void rejectsTamperedOrForeignTokens() {
		JwtTokenProvider provider = provider(SECRET, "issuer", at(NOW));
		String token = provider.issueAccessToken(1L).value();
		String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");

		assertThat(provider.parseAccessToken(tampered)).isEmpty();
		assertThat(provider("another-secret-another-secret-another-1", "issuer", at(NOW)).parseAccessToken(token)).isEmpty();
		assertThat(provider(SECRET, "other-issuer", at(NOW)).parseAccessToken(token)).isEmpty();
	}

	@Test
	@DisplayName("JWT가 아닌 값이나 빈 값은 거절한다")
	void rejectsGarbage() {
		JwtTokenProvider provider = provider(SECRET, "issuer", at(NOW));

		assertThat(provider.parseAccessToken("")).isEmpty();
		assertThat(provider.parseAccessToken("not-a-jwt")).isEmpty();
		assertThat(provider.parseAccessToken("a.b.c")).isEmpty();
	}

	@Test
	@DisplayName("비밀키가 32바이트보다 짧으면 시작하지 못하고, 없으면 임시 키로 동작한다")
	void validatesSecret() {
		assertThatThrownBy(() -> provider("short", "issuer", at(NOW))).isInstanceOf(IllegalStateException.class);

		JwtTokenProvider temporary = provider("", "issuer", at(NOW));
		assertThat(temporary.parseAccessToken(temporary.issueAccessToken(7L).value())).contains(7L);
		assertThat(provider("", "issuer", at(NOW)).parseAccessToken(temporary.issueAccessToken(7L).value())).isEmpty();
	}
}
