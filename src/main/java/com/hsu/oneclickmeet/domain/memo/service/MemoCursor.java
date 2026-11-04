package com.hsu.oneclickmeet.domain.memo.service;

import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

record MemoCursor(Instant createdAt, long memoId) {

	private static final int MAX_ENCODED_LENGTH = 160;
	private static final Instant MIN_DATE = Instant.parse("1000-01-01T00:00:00Z");
	private static final Instant MAX_DATE = Instant.parse("9999-12-31T23:59:59.999999Z");

	String encode(long authorId, int size) {
		String payload = scope(authorId, size) + createdAt + "|" + memoId;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	static MemoCursor decode(String encoded, long authorId, int size) {
		if (encoded == null) {
			return null;
		}
		if (encoded.length() > MAX_ENCODED_LENGTH) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
		try {
			String payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
			String scope = scope(authorId, size);
			if (payload.startsWith(scope)) {
				String[] position = payload.substring(scope.length()).split("[|]", -1);
				if (position.length == 2) {
					Instant createdAt = Instant.parse(position[0]);
					long memoId = Long.parseLong(position[1]);
					if (memoId > 0 && !createdAt.isBefore(MIN_DATE) && !createdAt.isAfter(MAX_DATE)) {
						return new MemoCursor(createdAt, memoId);
					}
				}
			}
		} catch (IllegalArgumentException | DateTimeParseException exception) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
		throw new BusinessException(CommonErrorCode.BAD_REQUEST);
	}

	private static String scope(long authorId, int size) {
		return "m1|" + authorId + "|" + size + "|";
	}
}
