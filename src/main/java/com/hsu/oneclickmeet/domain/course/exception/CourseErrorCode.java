package com.hsu.oneclickmeet.domain.course.exception;

import com.hsu.oneclickmeet.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum CourseErrorCode implements ErrorCode {
	NOT_FOUND(HttpStatus.NOT_FOUND, "COURSE-0404", "존재하지 않는 코스입니다."),
	SOURCE_PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "PLACE-0404", "장소를 찾을 수 없습니다."),
	IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "COURSE-0409-01", "이미 다른 요청에 사용한 멱등성 키입니다."),
	SAVE_IN_PROGRESS(HttpStatus.CONFLICT, "COURSE-0409-02", "같은 멱등성 키의 저장 요청을 처리 중입니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
