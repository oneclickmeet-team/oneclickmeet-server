package com.hsu.oneclickmeet.domain.place.dto.response;

import com.hsu.oneclickmeet.domain.place.client.PlaceCandidate;

public record PlaceCandidateResponse(
		String providerPlaceId,
		String name,
		String address,
		String roadAddress,
		String category,
		String categoryGroupCode,
		double lat,
		double lng,
		String placeUrl) {

	public static PlaceCandidateResponse from(PlaceCandidate candidate) {
		return new PlaceCandidateResponse(
				candidate.providerPlaceId(),
				candidate.name(),
				candidate.address(),
				candidate.roadAddress(),
				candidate.category(),
				candidate.categoryGroupCode(),
				candidate.lat(),
				candidate.lng(),
				candidate.placeUrl());
	}
}
