package com.hsu.oneclickmeet.domain.place.entity;

/** 보관함 장소에 붙는 블로그 언급 기반 태그(AI 서버가 판단한 결과를 그대로 보관한다). */
public record SavedPlaceTag(String label, int mentionCount) {
}
