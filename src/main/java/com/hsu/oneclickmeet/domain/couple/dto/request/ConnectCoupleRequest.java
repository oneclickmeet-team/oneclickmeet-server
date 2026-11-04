package com.hsu.oneclickmeet.domain.couple.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.JsonNode;

public record ConnectCoupleRequest(@NotBlank(message = "초대 코드를 문자열로 입력해 주세요.") String invitationCode) {

	@JsonCreator
	static ConnectCoupleRequest fromJson(@JsonProperty("invitationCode") JsonNode invitationCode) {
		String code = invitationCode != null && invitationCode.isString() ? invitationCode.stringValue() : null;
		return new ConnectCoupleRequest(code);
	}
}
