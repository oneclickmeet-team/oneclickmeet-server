package com.hsu.oneclickmeet.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMemberRequest(
		@NotBlank(message = "nickname은 필수입니다.")
		@Size(max = 30, message = "nickname은 30자 이하여야 합니다.")
		String nickname) {
}
