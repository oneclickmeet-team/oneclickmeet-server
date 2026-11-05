package com.hsu.oneclickmeet.domain.place.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SharedTextNameHeuristicTests {

	@Test
	@DisplayName("링크와 [앱 이름] 머리말을 지우고 남은 줄을 이름 후보로 쓴다")
	void removesLinksAndBracketTags() {
		String shared = "[네이버지도]\n연우재\n서울 성북구 성북로 15-6\nhttps://naver.me/abc123";

		assertThat(SharedTextNameHeuristic.extract(shared)).containsExactly("연우재", "서울 성북구 성북로 15-6");
	}

	@Test
	@DisplayName("한 줄에 이름과 링크가 함께 있어도 이름만 남긴다")
	void keepsNameOnSameLineAsLink() {
		assertThat(SharedTextNameHeuristic.extract("카머 https://place.map.kakao.com/1427027625"))
			.containsExactly("카머");
	}

	@Test
	@DisplayName("링크뿐이거나 너무 긴 줄만 있으면 후보가 없다")
	void returnsNothingForLinkOnlyOrTooLong() {
		assertThat(SharedTextNameHeuristic.extract("https://naver.me/abc123")).isEmpty();
		assertThat(SharedTextNameHeuristic.extract("가".repeat(51))).isEmpty();
	}

	@Test
	@DisplayName("후보는 최대 2개이고 중복은 하나로 합친다")
	void limitsAndDeduplicates() {
		assertThat(SharedTextNameHeuristic.extract("a\na\nb\nc")).containsExactly("a", "b");
	}
}
