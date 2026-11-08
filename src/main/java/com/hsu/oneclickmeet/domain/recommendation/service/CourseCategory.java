package com.hsu.oneclickmeet.domain.recommendation.service;

/** 방문 순서별로 지정하는 카테고리. 코드는 AI 서버의 카테고리 7개(API_CONVENTION v2.1)와 같다. */
public enum CourseCategory {
	MEAL,
	CAFE,
	BAR,
	ACTIVITY,
	CULTURE,
	WALK,
	SHOPPING;

	/** 카카오 카테고리 그룹 코드에서 알 수 있는 카테고리. 대응이 없으면 null이고 AI 서버가 추론한다. */
	public static CourseCategory fromKakaoGroupCode(String groupCode) {
		if (groupCode == null) {
			return null;
		}
		return switch (groupCode) {
			case "FD6" -> MEAL;
			case "CE7" -> CAFE;
			case "CT1" -> CULTURE;
			case "AT4" -> WALK;
			default -> null;
		};
	}
}
