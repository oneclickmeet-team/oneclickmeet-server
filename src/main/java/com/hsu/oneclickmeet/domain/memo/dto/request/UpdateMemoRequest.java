package com.hsu.oneclickmeet.domain.memo.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.CodePointLength;
import tools.jackson.databind.JsonNode;

public record UpdateMemoRequest(
	@NotNull(message = "메모 내용을 문자열로 입력해 주세요.")
	@Pattern(regexp = "(?sU).*\\S.*", message = "메모 내용을 입력해 주세요.")
	@Pattern(regexp = "[^\\p{Cs}]*", message = "메모 내용에 올바르지 않은 유니코드 문자가 있습니다.")
	@CodePointLength(max = 2_000, message = "메모는 2,000자 이하여야 합니다.") String content
) {
	@JsonCreator
	static UpdateMemoRequest fromJson(@JsonProperty("content") JsonNode content) {
		return new UpdateMemoRequest(content != null && content.isString() ? content.stringValue() : null);
	}
}
