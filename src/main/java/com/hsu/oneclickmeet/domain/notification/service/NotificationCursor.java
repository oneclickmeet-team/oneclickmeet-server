package com.hsu.oneclickmeet.domain.notification.service;

import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** 알림 목록의 다음 쪽 위치(마지막으로 본 id). 다른 회원의 커서는 거절한다. */
final class NotificationCursor {

	private static final int MAX_ENCODED_LENGTH = 100;

	private NotificationCursor() {
	}

	static String encode(long memberId, long lastId) {
		return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(("nt1|" + memberId + "|" + lastId).getBytes(StandardCharsets.UTF_8));
	}

	static Long decode(String encoded, long memberId) {
		if (encoded == null) {
			return null;
		}
		if (encoded.length() > MAX_ENCODED_LENGTH) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
		try {
			String payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
			String prefix = "nt1|" + memberId + "|";
			if (payload.startsWith(prefix)) {
				long id = Long.parseLong(payload.substring(prefix.length()));
				if (id > 0) {
					return id;
				}
			}
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
		throw new BusinessException(CommonErrorCode.BAD_REQUEST);
	}
}
