package com.hsu.oneclickmeet.domain.place.dto.response;

import com.hsu.oneclickmeet.domain.place.client.PlaceCandidate;
import com.hsu.oneclickmeet.domain.place.client.Recognition.RecognizedPlace;

import java.util.List;

/**
 * 링크·이미지로 찾은 후보. 검색 후보와 같은 필드에 매칭 정보를 더했다.
 * matchType이 EXACT면 기본 선택, SIMILAR면 접어서 matchReasons와 함께 보여 준다(AI 서버를 쓰지 못한 경우 null).
 * tags는 블로그 언급 기반 태그로, 근거가 약하면 빈 목록이다.
 */
public record ResolvedPlaceResponse(
		String providerPlaceId,
		String name,
		String address,
		String roadAddress,
		String category,
		String categoryGroupCode,
		double lat,
		double lng,
		String placeUrl,
		String matchType,
		List<String> matchReasons,
		List<TagResponse> tags) {

	public record TagResponse(String label, int mentionCount) {
	}

	public static ResolvedPlaceResponse from(RecognizedPlace place) {
		PlaceCandidate c = place.candidate();
		return new ResolvedPlaceResponse(c.providerPlaceId(), c.name(), c.address(), c.roadAddress(), c.category(),
				c.categoryGroupCode(), c.lat(), c.lng(), c.placeUrl(), place.matchType(), place.matchReasons(),
				place.tags().stream().map(tag -> new TagResponse(tag.label(), tag.mentionCount())).toList());
	}

	public static ResolvedPlaceResponse from(PlaceCandidate c) {
		return new ResolvedPlaceResponse(c.providerPlaceId(), c.name(), c.address(), c.roadAddress(), c.category(),
				c.categoryGroupCode(), c.lat(), c.lng(), c.placeUrl(), null, List.of(), List.of());
	}
}
