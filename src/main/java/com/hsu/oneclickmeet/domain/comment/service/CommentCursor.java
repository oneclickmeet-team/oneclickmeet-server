package com.hsu.oneclickmeet.domain.comment.service;

import com.hsu.oneclickmeet.domain.comment.entity.CommentTargetType;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

record CommentCursor(Instant createdAt, long commentId) {

	private static final Instant MIN_DATE = Instant.parse("1000-01-01T00:00:00Z");
	private static final Instant MAX_DATE = Instant.parse("9999-12-31T23:59:59.999999Z");

	String encode(CommentTargetType type, long targetId, int size) {
		String value = scope(type, targetId, size) + createdAt + "|" + commentId;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	static CommentCursor decode(String cursor, CommentTargetType type, long targetId, int size) {
		if (cursor == null) {
			return null;
		}
		String scope = scope(type, targetId, size);
		if (cursor.length() <= 160) {
			try {
				String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
				if (value.startsWith(scope)) {
					String[] position = value.substring(scope.length()).split("\\|", -1);
					if (position.length == 2) {
						Instant time = Instant.parse(position[0]);
						long id = Long.parseLong(position[1]);
						if (id > 0 && !time.isBefore(MIN_DATE) && !time.isAfter(MAX_DATE)) {
							return new CommentCursor(time, id);
						}
					}
				}
			} catch (IllegalArgumentException | DateTimeParseException exception) {
				throw new BusinessException(CommonErrorCode.BAD_REQUEST);
			}
		}
		throw new BusinessException(CommonErrorCode.BAD_REQUEST);
	}

	private static String scope(CommentTargetType type, long targetId, int size) {
		return "comments-1|" + type + "|" + targetId + "|" + size + "|";
	}
}
