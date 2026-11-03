package com.hsu.oneclickmeet.domain.place.client.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 카카오 키워드 장소 검색 응답. 이 패키지 밖으로 노출하지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoKeywordSearchResponse(List<Document> documents, Meta meta) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Document(
			String id,
			@JsonProperty("place_name") String placeName,
			@JsonProperty("category_name") String categoryName,
			@JsonProperty("category_group_code") String categoryGroupCode,
			@JsonProperty("address_name") String addressName,
			@JsonProperty("road_address_name") String roadAddressName,
			String x,
			String y,
			@JsonProperty("place_url") String placeUrl) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Meta(@JsonProperty("is_end") Boolean isEnd) {
	}
}
