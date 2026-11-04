package com.hsu.oneclickmeet.domain.comment.dto.response;

import com.hsu.oneclickmeet.domain.comment.entity.CommentTargetType;

import java.time.Instant;

public record CommentResponse(long commentId, CommentTargetType targetType, long targetId, Author author, String content,
		boolean isMine, boolean archived, Instant archivedAt, boolean readOnly, boolean editable, boolean deletable,
		Instant createdAt, Instant updatedAt) {

	public record Author(long memberId, String displayName) {
	}
}
