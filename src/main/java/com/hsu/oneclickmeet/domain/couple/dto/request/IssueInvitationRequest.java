package com.hsu.oneclickmeet.domain.couple.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record IssueInvitationRequest(
		@NotNull(message = "초대 목적은 필수입니다.")
		@Pattern(regexp = "CONNECT|RECONNECT", message = "초대 목적은 CONNECT 또는 RECONNECT여야 합니다.")
		String purpose) {
}
