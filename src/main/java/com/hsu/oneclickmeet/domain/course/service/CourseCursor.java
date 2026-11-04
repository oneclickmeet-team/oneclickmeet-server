package com.hsu.oneclickmeet.domain.course.service;

import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Locale;

record CourseCursor(Instant createdAt, long courseId) {

	private static final String PERSONAL_SCOPE = "1";
	private static final String SHARED_SCOPE = "s1";
	private static final int MAX_ENCODED_LENGTH = 128;
	private static final Instant MIN_DATE = Instant.parse("1000-01-01T00:00:00Z");
	private static final Instant MAX_DATE = Instant.parse("9999-12-31T23:59:59.999999Z");

	String encode(long memberId, int size) {
		return encode(PERSONAL_SCOPE, memberId, size);
	}

	String encodeShared(long coupleId, int size) {
		return encode(SHARED_SCOPE, coupleId, size);
	}

	private String encode(String scope, long ownerId, int size) {
		String payload = String.format(Locale.ROOT, "%s|%d|%d|%s|%d", scope, ownerId, size, createdAt, courseId);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	static CourseCursor decode(String encoded, long memberId, int size) {
		return decode(encoded, PERSONAL_SCOPE, memberId, size);
	}

	static CourseCursor decodeShared(String encoded, long coupleId, int size) {
		return decode(encoded, SHARED_SCOPE, coupleId, size);
	}

	private static CourseCursor decode(String encoded, String scope, long ownerId, int size) {
		if (encoded == null) {
			return null;
		}
		if (encoded.length() > MAX_ENCODED_LENGTH) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
		try {
			String payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
			String[] fields = payload.split("\\|", -1);
			if (fields.length == 5 && fields[0].equals(scope) && fields[1].equals(Long.toString(ownerId))
					&& fields[2].equals(Integer.toString(size))) {
				Instant createdAt = Instant.parse(fields[3]);
				long courseId = Long.parseLong(fields[4]);
				if (courseId > 0 && !createdAt.isBefore(MIN_DATE) && !createdAt.isAfter(MAX_DATE)) {
					return new CourseCursor(createdAt, courseId);
				}
			}
		} catch (IllegalArgumentException | DateTimeParseException exception) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
		throw new BusinessException(CommonErrorCode.BAD_REQUEST);
	}
}
