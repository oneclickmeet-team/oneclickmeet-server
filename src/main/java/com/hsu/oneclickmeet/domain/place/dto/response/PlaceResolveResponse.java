package com.hsu.oneclickmeet.domain.place.dto.response;

import java.util.List;

/**
 * 링크·이미지로 장소를 찾은 결과. CANDIDATES_FOUND면 후보를 골라 저장하고,
 * NEEDS_SEARCH면 앱이 직접 검색 화면으로 안내한다.
 */
public record PlaceResolveResponse(Status status, List<PlaceCandidateResponse> candidates) {

	public enum Status {
		CANDIDATES_FOUND,
		NEEDS_SEARCH
	}

	public static PlaceResolveResponse of(List<PlaceCandidateResponse> candidates) {
		return candidates.isEmpty() ? needsSearch() : new PlaceResolveResponse(Status.CANDIDATES_FOUND, candidates);
	}

	public static PlaceResolveResponse needsSearch() {
		return new PlaceResolveResponse(Status.NEEDS_SEARCH, List.of());
	}
}
