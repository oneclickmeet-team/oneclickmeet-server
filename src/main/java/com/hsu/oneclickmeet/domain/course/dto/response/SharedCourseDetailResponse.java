package com.hsu.oneclickmeet.domain.course.dto.response;

import com.hsu.oneclickmeet.domain.couple.dto.response.PartnerResponse;
import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseDetailResponse.Warning;

import java.time.Instant;
import java.util.List;

public record SharedCourseDetailResponse(long sharedCourseId, long coupleId, CoupleStatus relationshipStatus,
		boolean readOnly, Instant graceEndsAt, long structureVersion, PartnerResponse lastEditedBy, String courseName,
		List<Place> places, long selectedTotalPrice, long placesWithoutSelectedMenusCount, String walkingTimeSource,
		List<Warning> warnings, Instant createdAt, Instant updatedAt) {

	public record Place(long coursePlaceId, Long placeId, String placeName, Double latitude, Double longitude,
			Integer estimatedWalkingMinutesFromPrevious, List<Menu> menuList, long selectedSubtotal) {
	}

	public record Menu(long menuId, String menuName, int price, boolean selected, Instant updatedAt,
			PartnerResponse lastEditedBy) {
	}
}
