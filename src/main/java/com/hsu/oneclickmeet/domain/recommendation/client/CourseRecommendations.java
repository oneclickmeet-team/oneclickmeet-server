package com.hsu.oneclickmeet.domain.recommendation.client;

import java.util.List;

/** AI 서버의 추천 결과. 코스가 없으면 courses가 비고 reason에 이유가 들어 있다. */
public record CourseRecommendations(List<Course> courses, String reason) {

	public CourseRecommendations {
		courses = courses == null ? List.of() : List.copyOf(courses);
	}

	public record Course(String candidateKey, String title, int totalWalkMinutes, List<Place> places) {
	}

	/**
	 * source가 PERSONAL이면 placeId는 요청에 담아 보낸 보관함 장소 번호이고, EXTERNAL이면 null이다.
	 * providerPlaceId는 카카오 장소 ID일 때만 채운다.
	 */
	public record Place(int order, String source, boolean anchor, Long placeId, String providerPlaceId, String name,
			String address, String roadAddress, double lat, double lng, String categoryCode, String categoryLabel,
			String placeUrl, Integer walkMinutesFromPrevious) {
	}
}
