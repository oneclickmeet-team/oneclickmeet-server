package com.hsu.oneclickmeet.domain.place.dto.response;

import java.util.List;

public record PlaceSearchResponse(List<PlaceCandidateResponse> candidates, int page, boolean hasNext) {

	public static PlaceSearchResponse empty(int page) {
		return new PlaceSearchResponse(List.of(), page, false);
	}
}
