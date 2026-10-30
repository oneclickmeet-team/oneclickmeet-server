package com.hsu.oneclickmeet.domain.place.client;

/**
 * 장소 검색 요청. lat·lng는 둘 다 있을 때만 근처 우선 검색에 사용한다.
 */
public record PlaceSearchCommand(String query, int page, int size, Double lat, Double lng) {

	public boolean hasLocation() {
		return lat != null && lng != null;
	}
}
