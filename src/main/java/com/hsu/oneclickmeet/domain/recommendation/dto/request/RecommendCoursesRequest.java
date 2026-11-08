package com.hsu.oneclickmeet.domain.recommendation.dto.request;

import com.hsu.oneclickmeet.domain.recommendation.service.CourseCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 코스 추천 요청(TRD 6.1). 기준은 지역 또는 내 보관함 장소 중 하나이고, 방문 순서별 카테고리를 지정한다.
 * 기준이 장소(PLACE)이면 그 장소가 requiredPlaceOrder번째 방문 위치에 고정되고, 지역(REGION)이면 보내지 않는다.
 * excludeCandidateKeys는 다시 추천받을 때 앞에서 받은 candidateKey를 모두 담는다.
 */
public record RecommendCoursesRequest(
		@NotNull(message = "anchor는 필수입니다.") @Valid
		Anchor anchor,
		@NotNull(message = "placeCount는 필수입니다.")
		@Min(value = 3, message = "placeCount는 3 이상이어야 합니다.")
		@Max(value = 5, message = "placeCount는 5 이하여야 합니다.")
		Integer placeCount,
		@NotEmpty(message = "categorySequence는 필수입니다.")
		List<@NotNull(message = "categorySequence에 빈 값이 있습니다.") CourseCategory> categorySequence,
		Integer requiredPlaceOrder,
		@Size(max = 50, message = "excludeCandidateKeys는 50개 이하여야 합니다.")
		List<@NotBlank(message = "excludeCandidateKeys에 빈 값이 있습니다.") String> excludeCandidateKeys) {

	public enum AnchorType {
		REGION,
		PLACE
	}

	/** REGION이면 regionId, PLACE이면 보관함 장소 번호(placeId)를 보낸다. */
	public record Anchor(
			@NotNull(message = "anchor.type은 필수입니다.")
			AnchorType type,
			@Size(max = 30, message = "regionId는 30자 이하여야 합니다.")
			String regionId,
			@Positive(message = "placeId는 양의 정수여야 합니다.")
			Long placeId) {

		@AssertTrue(message = "REGION 기준은 regionId만, PLACE 기준은 placeId만 보내야 합니다.")
		public boolean isConsistent() {
			if (type == null) {
				return true;
			}
			boolean hasRegion = regionId != null && !regionId.isBlank();
			return type == AnchorType.REGION ? hasRegion && placeId == null : !hasRegion && placeId != null;
		}
	}

	@AssertTrue(message = "categorySequence의 개수는 placeCount와 같아야 합니다.")
	public boolean isSequenceSizeMatchingPlaceCount() {
		return placeCount == null || categorySequence == null || categorySequence.size() == placeCount;
	}

	@AssertTrue(message = "PLACE 기준은 1~placeCount의 requiredPlaceOrder가 필요하고, REGION 기준은 보내지 않아야 합니다.")
	public boolean isRequiredPlaceOrderConsistent() {
		if (anchor == null || anchor.type() == null || placeCount == null) {
			return true;
		}
		if (anchor.type() == AnchorType.REGION) {
			return requiredPlaceOrder == null;
		}
		return requiredPlaceOrder != null && requiredPlaceOrder >= 1 && requiredPlaceOrder <= placeCount;
	}
}
