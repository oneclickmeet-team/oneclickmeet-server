package com.hsu.oneclickmeet.domain.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnregisterDeviceRequest(
		@NotBlank(message = "token은 필수입니다.")
		@Size(max = 512, message = "token은 512자 이하여야 합니다.")
		String token) {
}
