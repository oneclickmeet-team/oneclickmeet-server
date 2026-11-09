package com.hsu.oneclickmeet.domain.place.service;

/** 보관함 장소 주변에서 찾을 놀거리 종류와 카카오 검색어. */
public enum NearbyCategory {
	CAFE("카페"),
	RESTAURANT("맛집"),
	EXHIBITION("전시"),
	PARK("공원");

	private final String keyword;

	NearbyCategory(String keyword) {
		this.keyword = keyword;
	}

	public String keyword() {
		return keyword;
	}
}
