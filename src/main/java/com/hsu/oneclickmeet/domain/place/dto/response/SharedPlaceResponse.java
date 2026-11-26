package com.hsu.oneclickmeet.domain.place.dto.response;

import com.hsu.oneclickmeet.domain.couple.dto.response.PartnerResponse;
import com.hsu.oneclickmeet.domain.place.entity.SharedPlace;

import java.time.Instant;
import java.util.List;

public record SharedPlaceResponse(
		long sharedPlaceId,
		String providerPlaceId,
		String name,
		String address,
		String roadAddress,
		String category,
		String categoryGroupCode,
		double lat,
		double lng,
		String placeUrl,
		List<ResolvedPlaceResponse.TagResponse> tags,
		Instant sharedAt,
		PartnerResponse sharedBy) {

	public static SharedPlaceResponse from(SharedPlace place, PartnerResponse sharedBy) {
		return new SharedPlaceResponse(place.getId(), place.getProviderPlaceId(), place.getName(), place.getAddress(),
				place.getRoadAddress(), place.getCategory(), place.getCategoryGroupCode(), place.getLat(), place.getLng(),
				place.getPlaceUrl(), place.getTags().stream()
						.map(tag -> new ResolvedPlaceResponse.TagResponse(tag.label(), tag.mentionCount())).toList(),
				place.getSharedAt(), sharedBy);
	}
}
