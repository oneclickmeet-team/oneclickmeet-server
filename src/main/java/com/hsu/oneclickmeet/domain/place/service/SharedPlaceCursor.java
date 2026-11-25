package com.hsu.oneclickmeet.domain.place.service;

import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class SharedPlaceCursor {

	private static final int MAX_ENCODED_LENGTH = 80;

	private SharedPlaceCursor() {
	}

	static String encode(long coupleId, long lastId) {
		String payload = prefix(coupleId) + lastId;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	static Long decode(String cursor, long coupleId) {
		if (cursor == null) {
			return null;
		}
		if (cursor.length() > MAX_ENCODED_LENGTH) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
		try {
			String payload = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			String prefix = prefix(coupleId);
			if (payload.startsWith(prefix)) {
				long lastId = Long.parseLong(payload.substring(prefix.length()));
				if (lastId > 0) {
					return lastId;
				}
			}
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
		throw new BusinessException(CommonErrorCode.BAD_REQUEST);
	}

	private static String prefix(long coupleId) {
		return "shared-place:" + coupleId + ":";
	}
}
