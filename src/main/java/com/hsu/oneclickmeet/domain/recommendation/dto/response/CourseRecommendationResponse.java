package com.hsu.oneclickmeet.domain.recommendation.dto.response;

import java.util.List;

/**
 * 코스 추천 결과. 추천은 저장하기 전까지 DB에 저장하지 않는다.
 * 후보가 부족하면 NOT_ENOUGH_CANDIDATES와 빈 목록을 돌려주며, 지역을 자동으로 넓히지 않는다.
 */
public record CourseRecommendationResponse(Status status, List<RecommendedCourse> courses) {

	public enum Status {
		OK,
		NOT_ENOUGH_CANDIDATES
	}

	public record RecommendedCourse(String candidateKey, int totalWalkMinutes, List<RecommendedPlace> places) {
	}

	public record RecommendedPlace(
			int order,
			String providerPlaceId,
			String name,
			String address,
			String roadAddress,
			String category,
			String categoryGroupCode,
			double lat,
			double lng,
			String placeUrl,
			Long savedPlaceId,
			Integer walkMinutesFromPrevious) {
	}

	public static CourseRecommendationResponse notEnough() {
		return new CourseRecommendationResponse(Status.NOT_ENOUGH_CANDIDATES, List.of());
	}
}
