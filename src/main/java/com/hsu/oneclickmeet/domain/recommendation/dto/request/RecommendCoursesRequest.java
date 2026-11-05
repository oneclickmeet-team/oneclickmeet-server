package com.hsu.oneclickmeet.domain.recommendation.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 코스 추천 요청. 기준 장소(보관함)와 지역 중 하나만 보낸다. */
public record RecommendCoursesRequest(
		@Positive(message = "anchorSavedPlaceId는 양의 정수여야 합니다.")
		Long anchorSavedPlaceId,
		@Size(max = 30, message = "area는 30자 이하여야 합니다.")
		String area) {
}
