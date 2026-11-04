package com.hsu.oneclickmeet.domain.memo.dto.response;

import com.hsu.oneclickmeet.domain.memo.entity.MemoTargetType;

import java.time.Instant;

public record MemoResponse(long memoId, MemoTargetType targetType, long targetId, String content,
		Instant createdAt, Instant updatedAt) {
}
