package com.hsu.oneclickmeet.domain.place.service;

import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/** 보관함 목록의 다음 쪽 위치. 다른 회원이나 다른 분류 필터에서 만든 커서는 거절한다. */
record SavedPlaceCursor(Instant createdAt, long id) {

	private static final int MAX_ENCODED_LENGTH = 200;
	private static final Instant MIN_DATE = Instant.parse("1000-01-01T00:00:00Z");
	private static final Instant MAX_DATE = Instant.parse("9999-12-31T23:59:59.999999Z");

	String encode(long memberId, String categoryGroupCode) {
		String payload = scope(memberId, categoryGroupCode) + createdAt + "|" + id;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	static SavedPlaceCursor decode(String encoded, long memberId, String categoryGroupCode) {
		if (encoded == null) {
			return null;
		}
		if (encoded.length() > MAX_ENCODED_LENGTH) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
		try {
			String payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
			String scope = scope(memberId, categoryGroupCode);
			if (payload.startsWith(scope)) {
				String[] position = payload.substring(scope.length()).split("[|]", -1);
				if (position.length == 2) {
					Instant createdAt = Instant.parse(position[0]);
					long id = Long.parseLong(position[1]);
					if (id > 0 && !createdAt.isBefore(MIN_DATE) && !createdAt.isAfter(MAX_DATE)) {
						return new SavedPlaceCursor(createdAt, id);
					}
				}
			}
		} catch (IllegalArgumentException | DateTimeParseException exception) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
		throw new BusinessException(CommonErrorCode.BAD_REQUEST);
	}

	private static String scope(long memberId, String categoryGroupCode) {
		return "sp1|" + memberId + "|" + (categoryGroupCode == null ? "" : categoryGroupCode) + "|";
	}
}
