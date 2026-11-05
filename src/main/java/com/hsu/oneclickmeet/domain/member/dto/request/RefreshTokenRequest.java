package com.hsu.oneclickmeet.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
		@NotBlank(message = "refreshToken 값이 필요합니다.")
		@Size(max = 200, message = "refreshToken 값이 너무 깁니다.")
		String refreshToken) {
}
