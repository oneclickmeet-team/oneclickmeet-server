package com.hsu.oneclickmeet.domain.recommendation.client;

import java.util.List;

/**
 * AI 서버에 보낼 추천 요청. 지역 기준이면 regionId만, 장소 기준이면 anchorPlace와 requiredPlaceOrder를 채운다.
 * savedPlaces는 추천 재료로 쓸 내 보관함 장소이며 기준 장소는 넣지 않는다(최대 500곳).
 */
public record CourseRecommendationCommand(
		String regionId,
		SavedPlaceInput anchorPlace,
		int placeCount,
		List<String> categorySequence,
		Integer requiredPlaceOrder,
		List<SavedPlaceInput> savedPlaces,
		List<String> excludeCandidateKeys) {

	/** categoryCode가 null이면 AI 서버가 추론한다. address는 도로명 주소가 있으면 그것을 쓴다. */
	public record SavedPlaceInput(long placeId, String name, String address, double latitude, double longitude,
			String categoryCode) {
	}
}
