package com.hsu.oneclickmeet.domain.member.dto.response;

/** 발급한 토큰. expiresIn은 접근 토큰이 유효한 시간(초)이다. */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {

	public static TokenResponse bearer(String accessToken, String refreshToken, long expiresIn) {
		return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn);
	}
}
