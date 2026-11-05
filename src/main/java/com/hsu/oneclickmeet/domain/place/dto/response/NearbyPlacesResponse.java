package com.hsu.oneclickmeet.domain.place.dto.response;

import com.hsu.oneclickmeet.domain.place.service.NearbyCategory;

import java.util.List;

public record NearbyPlacesResponse(long savedPlaceId, NearbyCategory category, List<NearbyPlaceResponse> items) {
}
