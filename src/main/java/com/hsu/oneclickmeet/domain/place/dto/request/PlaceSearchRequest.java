package com.hsu.oneclickmeet.domain.place.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 장소 검색 요청. page·size를 생략하면 기본값(1, 10)을 쓴다.
 */
public record PlaceSearchRequest(
		@NotBlank(message = "검색어는 필수입니다.")
		@Size(max = 100, message = "검색어는 100자 이하여야 합니다.")
		String query,
		@Min(value = 1, message = "page는 1 이상이어야 합니다.")
		Integer page,
		@Min(value = 1, message = "size는 1 이상이어야 합니다.")
		@Max(value = 15, message = "size는 15 이하여야 합니다.")
		Integer size) {

	public static final int DEFAULT_PAGE = 1;
	public static final int DEFAULT_SIZE = 10;

	public PlaceSearchRequest {
		if (page == null) {
			page = DEFAULT_PAGE;
		}
		if (size == null) {
			size = DEFAULT_SIZE;
		}
	}
}
