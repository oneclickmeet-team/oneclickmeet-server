package com.hsu.oneclickmeet.domain.place.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 보관함 저장 요청. 앱이 검색 결과에서 고른 후보의 값을 그대로 보낸다.
 * 회원은 인증 정보에서 얻으므로 요청에 포함하지 않는다.
 */
public record SaveSavedPlaceRequest(
		@NotBlank(message = "providerPlaceId는 필수입니다.")
		@Size(max = 64, message = "providerPlaceId는 64자 이하여야 합니다.")
		@Pattern(regexp = "\\S*", message = "providerPlaceId에는 공백을 쓸 수 없습니다.")
		String providerPlaceId,
		@NotBlank(message = "name은 필수입니다.")
		@Size(max = 200, message = "name은 200자 이하여야 합니다.")
		String name,
		@NotBlank(message = "address는 필수입니다.")
		@Size(max = 300, message = "address는 300자 이하여야 합니다.")
		String address,
		@Size(max = 300, message = "roadAddress는 300자 이하여야 합니다.")
		String roadAddress,
		@Size(max = 200, message = "category는 200자 이하여야 합니다.")
		String category,
		@Size(max = 10, message = "categoryGroupCode는 10자 이하여야 합니다.")
		String categoryGroupCode,
		@NotNull(message = "lat는 필수입니다.")
		@DecimalMin(value = "-90.0", message = "lat는 -90 이상이어야 합니다.")
		@DecimalMax(value = "90.0", message = "lat는 90 이하여야 합니다.")
		Double lat,
		@NotNull(message = "lng는 필수입니다.")
		@DecimalMin(value = "-180.0", message = "lng는 -180 이상이어야 합니다.")
		@DecimalMax(value = "180.0", message = "lng는 180 이하여야 합니다.")
		Double lng,
		@Size(max = 500, message = "placeUrl은 500자 이하여야 합니다.")
		String placeUrl) {
}
