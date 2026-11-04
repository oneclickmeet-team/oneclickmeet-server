package com.hsu.oneclickmeet.domain.course.dto.response;

import java.time.Instant;
import java.util.List;

public record CourseDetailResponse(long courseId, String courseName, List<Place> places, long selectedTotalPrice,
		long placesWithoutSelectedMenusCount, String walkingTimeSource, List<Warning> warnings,
		Instant createdAt, Instant updatedAt) {

	public record Place(long coursePlaceId, Long placeId, String placeName, Double latitude, Double longitude,
			Integer estimatedWalkingMinutesFromPrevious, List<Menu> menuList, long selectedSubtotal) {
	}

	public record Menu(long menuId, String menuName, int price, boolean selected, Instant updatedAt) {
	}

	public record Warning(String code, long coursePlaceId, int estimatedMinutes) {
	}
}
