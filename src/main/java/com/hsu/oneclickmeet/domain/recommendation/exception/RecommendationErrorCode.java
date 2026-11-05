package com.hsu.oneclickmeet.domain.recommendation.exception;

import com.hsu.oneclickmeet.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum RecommendationErrorCode implements ErrorCode {
	START_REQUIRED(HttpStatus.BAD_REQUEST, "RECOMMENDATION-0400", "기준 장소나 지역 중 하나를 골라 주세요."),
	UNSUPPORTED_AREA(HttpStatus.BAD_REQUEST, "RECOMMENDATION-0401", "아직 지원하지 않는 지역이에요.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
