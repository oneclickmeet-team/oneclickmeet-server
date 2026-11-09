package com.hsu.oneclickmeet.domain.recommendation.service;

import java.util.Arrays;
import java.util.Optional;

/** 코스 추천을 지원하는 지역. 중심 좌표는 고정값을 쓰지 않고 카카오 검색으로 얻는다. */
public enum SupportedArea {
	JAMSIL("잠실", "잠실역"),
	SUNGSHIN("성신여대입구역", "성신여대입구역"),
	HANSUNG("한성대입구역", "한성대입구역"),
	HYEHWA("혜화", "혜화역"),
	SEOCHON("서촌", "서촌"),
	YEONNAM("연남", "연남동");

	private final String displayName;
	private final String searchKeyword;

	SupportedArea(String displayName, String searchKeyword) {
		this.displayName = displayName;
		this.searchKeyword = searchKeyword;
	}

	public String displayName() {
		return displayName;
	}

	public String searchKeyword() {
		return searchKeyword;
	}

	/** 화면에 보이는 이름이나 영문 이름(대소문자 무시)으로 찾는다. */
	public static Optional<SupportedArea> find(String value) {
		if (value == null) {
			return Optional.empty();
		}
		String trimmed = value.trim();
		return Arrays.stream(values())
				.filter(area -> area.displayName.equals(trimmed) || area.name().equalsIgnoreCase(trimmed))
				.findFirst();
	}
}
