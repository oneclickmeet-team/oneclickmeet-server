package com.hsu.oneclickmeet.domain.place.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 서버를 쓸 수 없을 때 공유 텍스트에서 장소 이름 후보를 뽑는 단순 규칙.
 * 링크(URL)와 [앱 이름] 같은 머리말을 지우고 남은 짧은 줄을 후보로 쓴다. 정확도는 보장하지 않으며,
 * 후보가 틀려도 사용자가 후보 목록에서 고르거나 직접 검색하는 흐름으로 이어진다.
 */
final class SharedTextNameHeuristic {

	static final int MAX_NAMES = 2;
	static final int MAX_LENGTH = 50;

	private SharedTextNameHeuristic() {
	}

	static List<String> extract(String sharedText) {
		Set<String> names = new LinkedHashSet<>();
		for (String rawLine : sharedText.split("\\R")) {
			String line = rawLine.replaceAll("https?://\\S+", " ")
					.replaceAll("\\[[^\\]]*]", " ")
					.replaceAll("\\s+", " ")
					.trim();
			if (!line.isEmpty() && line.length() <= MAX_LENGTH) {
				names.add(line);
			}
			if (names.size() == MAX_NAMES) {
				break;
			}
		}
		return List.copyOf(names);
	}
}
