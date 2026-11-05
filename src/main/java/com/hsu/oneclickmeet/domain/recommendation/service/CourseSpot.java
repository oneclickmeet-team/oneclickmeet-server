package com.hsu.oneclickmeet.domain.recommendation.service;

/** 코스 후보 장소 하나. savedPlaceId가 있으면 사용자의 보관함 장소다. */
public record CourseSpot(
		String providerPlaceId,
		String name,
		String address,
		String roadAddress,
		String category,
		String categoryGroupCode,
		double lat,
		double lng,
		String placeUrl,
		Long savedPlaceId) {

	public boolean saved() {
		return savedPlaceId != null;
	}
}
