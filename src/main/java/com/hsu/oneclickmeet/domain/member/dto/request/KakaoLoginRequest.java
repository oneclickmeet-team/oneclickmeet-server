package com.hsu.oneclickmeet.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KakaoLoginRequest(
		@NotBlank(message = "accessToken 값이 필요합니다.")
		@Size(max = 4096, message = "accessToken 값이 너무 깁니다.")
		String accessToken) {
}
