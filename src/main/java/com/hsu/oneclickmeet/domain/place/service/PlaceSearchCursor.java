package com.hsu.oneclickmeet.domain.place.service;

import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 장소 검색의 다음 쪽 위치(카카오 page 번호). 다른 검색어나 다른 size에서 만든 커서는 거절한다.
 * 첫 쪽은 커서 없이 요청하므로 커서의 쪽 번호는 항상 2 이상이다.
 */
final class PlaceSearchCursor {

	private static final int MAX_ENCODED_LENGTH = 200;

	private PlaceSearchCursor() {
	}

	static String encode(String query, int size, int page) {
		String payload = scope(query, size) + page;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	/** 커서가 없으면 첫 쪽(1)을 돌려준다. */
	static int decode(String encoded, String query, int size) {
		if (encoded == null) {
			return 1;
		}
		if (encoded.length() > MAX_ENCODED_LENGTH) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
		try {
			String payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
			String scope = scope(query, size);
			if (payload.startsWith(scope)) {
				int page = Integer.parseInt(payload.substring(scope.length()));
				if (page >= 2) {
					return page;
				}
			}
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
		throw new BusinessException(CommonErrorCode.BAD_REQUEST);
	}

	private static String scope(String query, int size) {
		return "ps1|" + Integer.toHexString(query.hashCode()) + "|" + size + "|";
	}
}
