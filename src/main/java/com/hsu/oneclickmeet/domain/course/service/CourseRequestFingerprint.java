package com.hsu.oneclickmeet.domain.course.service;

import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class CourseRequestFingerprint {
	private static final JsonMapper MAPPER = JsonMapper.builder()
			.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
			.build();

	private CourseRequestFingerprint() {
	}

	static String hash(Object request) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(MAPPER.writeValueAsBytes(request));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
		}
	}
}
