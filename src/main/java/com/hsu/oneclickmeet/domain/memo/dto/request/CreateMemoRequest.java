package com.hsu.oneclickmeet.domain.memo.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hsu.oneclickmeet.domain.memo.entity.MemoTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.CodePointLength;
import tools.jackson.databind.JsonNode;

public record CreateMemoRequest(
	@NotNull(message = "지원하는 메모 대상 유형을 입력해 주세요.") MemoTargetType targetType,
	@NotNull(message = "대상 ID를 정수로 입력해 주세요.") @Positive Long targetId,
	@NotNull(message = "메모 내용을 문자열로 입력해 주세요.")
	@Pattern(regexp = "(?sU).*\\S.*", message = "메모 내용을 입력해 주세요.")
	@Pattern(regexp = "[^\\p{Cs}]*", message = "메모 내용에 올바르지 않은 유니코드 문자가 있습니다.")
	@CodePointLength(max = 2_000, message = "메모는 2,000자 이하여야 합니다.") String content
) {
	@JsonCreator
	static CreateMemoRequest fromJson(@JsonProperty("targetType") JsonNode targetType,
			@JsonProperty("targetId") JsonNode targetId, @JsonProperty("content") JsonNode content) {
		Long id = targetId != null && targetId.isIntegralNumber() && targetId.canConvertToLong()
			? targetId.longValue() : null;
		String text = content != null && content.isString() ? content.stringValue() : null;
		return new CreateMemoRequest(parseType(targetType), id, text);
	}

	private static MemoTargetType parseType(JsonNode targetType) {
		if (targetType == null || !targetType.isString()) {
			return null;
		}
		try {
			return MemoTargetType.valueOf(targetType.stringValue());
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}
}
