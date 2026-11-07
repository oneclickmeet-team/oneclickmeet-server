package com.hsu.oneclickmeet.domain.place.dto.response;

import java.util.List;

/**
 * 링크·이미지로 장소를 찾은 결과. CANDIDATES_FOUND면 후보를 골라 저장하고,
 * NEEDS_SEARCH면 앱이 직접 검색 화면으로 안내하며 extractedQuery가 있으면 검색창에 채운다.
 */
public record PlaceResolveResponse(Status status, String extractedQuery, List<ResolvedPlaceResponse> candidates) {

	public enum Status {
		CANDIDATES_FOUND,
		NEEDS_SEARCH
	}

	public static PlaceResolveResponse of(String extractedQuery, List<ResolvedPlaceResponse> candidates) {
		return candidates.isEmpty() ? needsSearch(extractedQuery)
				: new PlaceResolveResponse(Status.CANDIDATES_FOUND, extractedQuery, candidates);
	}

	public static PlaceResolveResponse needsSearch(String extractedQuery) {
		return new PlaceResolveResponse(Status.NEEDS_SEARCH, extractedQuery, List.of());
	}
}
