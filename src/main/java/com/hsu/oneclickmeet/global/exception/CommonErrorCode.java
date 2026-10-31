package com.hsu.oneclickmeet.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {
	BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON-0400", "입력값을 확인해 주세요."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-0500", "요청 처리 중 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
