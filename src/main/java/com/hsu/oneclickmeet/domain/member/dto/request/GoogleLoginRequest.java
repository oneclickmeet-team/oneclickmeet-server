package com.hsu.oneclickmeet.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleLoginRequest(
		@NotBlank(message = "idToken 값이 필요합니다.")
		@Size(max = 4096, message = "idToken 값이 너무 깁니다.")
		String idToken) {
}
