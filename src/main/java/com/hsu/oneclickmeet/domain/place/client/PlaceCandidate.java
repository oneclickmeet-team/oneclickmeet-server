package com.hsu.oneclickmeet.domain.place.client;

public record PlaceCandidate(
		String providerPlaceId,
		String name,
		String address,
		String roadAddress,
		String category,
		String categoryGroupCode,
		double lat,
		double lng,
		String placeUrl) {
}
