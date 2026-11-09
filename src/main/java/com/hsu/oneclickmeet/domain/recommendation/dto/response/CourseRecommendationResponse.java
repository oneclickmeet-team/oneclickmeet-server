package com.hsu.oneclickmeet.domain.recommendation.dto.response;

import java.util.List;

/**
 * 코스 추천 결과. 추천은 저장하기 전까지 DB에 저장하지 않는다.
 * 후보가 부족하면 NOT_ENOUGH_CANDIDATES와 빈 목록, 그 이유(reason)를 돌려주며 지역을 자동으로 넓히지 않는다.
 * reason은 AI 서버가 준 INSUFFICIENT_PLACES·UNSUPPORTED_REGION·NO_MORE_COURSES 중 하나이고, OK이면 null이다.
 */
public record CourseRecommendationResponse(Status status, String reason, List<RecommendedCourse> courses) {

	public enum Status {
		OK,
		NOT_ENOUGH_CANDIDATES
	}

	public record RecommendedCourse(String candidateKey, String title, int totalWalkMinutes,
			List<RecommendedPlace> places) {
	}

	/**
	 * source는 PERSONAL(내 보관함) 또는 EXTERNAL(외부 후보)이다. 내 보관함 장소만 savedPlaceId가 있다.
	 * anchor는 기준(필수) 장소인지 여부, category는 화면에 보일 이름, categoryCode는 7개 코드 중 하나다.
	 */
	public record RecommendedPlace(
			int order,
			String source,
			boolean anchor,
			String providerPlaceId,
			String name,
			String address,
			String roadAddress,
			String category,
			String categoryCode,
			double lat,
			double lng,
			String placeUrl,
			Long savedPlaceId,
			Integer walkMinutesFromPrevious) {
	}

	public static CourseRecommendationResponse notEnough(String reason) {
		return new CourseRecommendationResponse(Status.NOT_ENOUGH_CANDIDATES, reason, List.of());
	}
}
