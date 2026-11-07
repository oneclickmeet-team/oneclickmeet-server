package com.hsu.oneclickmeet.domain.place.client;

import java.util.List;

/** AI 서버의 장소 인식 결과. 후보가 없으면 직접 검색으로 안내하며 extractedQuery를 검색어로 쓴다. */
public record Recognition(String extractedQuery, List<RecognizedPlace> places) {

	public Recognition {
		places = places == null ? List.of() : List.copyOf(places);
	}

	/** 인식 결과 후보 하나. 매칭 근거와 블로그 언급 기반 태그를 함께 가진다. */
	public record RecognizedPlace(PlaceCandidate candidate, String matchType, List<String> matchReasons,
			List<Tag> tags) {

		public RecognizedPlace {
			matchReasons = matchReasons == null ? List.of() : List.copyOf(matchReasons);
			tags = tags == null ? List.of() : List.copyOf(tags);
		}
	}

	public record Tag(String label, int mentionCount) {
	}
}
