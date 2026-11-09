package com.hsu.oneclickmeet.domain.place.dto.response;

import java.util.List;

public record PlaceSearchResponse(List<PlaceCandidateResponse> items, String nextCursor, boolean hasNext) {

	public static PlaceSearchResponse empty() {
		return new PlaceSearchResponse(List.of(), null, false);
	}
}
