package com.hsu.oneclickmeet.domain.place.dto.response;

import com.hsu.oneclickmeet.domain.place.entity.SavedPlace;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public record SavedPlaceResponse(
		long savedPlaceId,
		String providerPlaceId,
		String name,
		String address,
		String roadAddress,
		String category,
		String categoryGroupCode,
		double lat,
		double lng,
		String placeUrl,
		Instant savedAt,
		List<ResolvedPlaceResponse.TagResponse> tags) {

	public static SavedPlaceResponse from(SavedPlace place) {
		return new SavedPlaceResponse(
				place.getId(),
				place.getProviderPlaceId(),
				place.getName(),
				place.getAddress(),
				place.getRoadAddress(),
				place.getCategory(),
				place.getCategoryGroupCode(),
				place.getLat(),
				place.getLng(),
				place.getPlaceUrl(),
				place.getCreatedAt() == null ? null : place.getCreatedAt().truncatedTo(ChronoUnit.MICROS),
				place.getTags().stream()
						.map(tag -> new ResolvedPlaceResponse.TagResponse(tag.label(), tag.mentionCount()))
						.toList());
	}
}
