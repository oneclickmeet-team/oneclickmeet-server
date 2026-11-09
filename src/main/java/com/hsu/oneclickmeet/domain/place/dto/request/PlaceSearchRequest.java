package com.hsu.oneclickmeet.domain.place.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 장소 검색 요청. cursor는 이전 응답의 nextCursor이며 첫 쪽에서는 생략한다. size를 생략하면 기본값(10)을 쓴다.
 */
public record PlaceSearchRequest(
		@NotBlank(message = "검색어는 필수입니다.")
		@Size(max = 100, message = "검색어는 100자 이하여야 합니다.")
		String query,
		@Size(max = 200, message = "cursor가 올바르지 않습니다.")
		String cursor,
		@Min(value = 1, message = "size는 1 이상이어야 합니다.")
		@Max(value = 15, message = "size는 15 이하여야 합니다.")
		Integer size) {

	public static final int DEFAULT_SIZE = 10;

	public PlaceSearchRequest {
		if (size == null) {
			size = DEFAULT_SIZE;
		}
	}
}
