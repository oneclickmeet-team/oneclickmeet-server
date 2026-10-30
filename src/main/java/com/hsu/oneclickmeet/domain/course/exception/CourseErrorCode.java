package com.hsu.oneclickmeet.domain.course.exception;

import com.hsu.oneclickmeet.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum CourseErrorCode implements ErrorCode {
	NOT_FOUND(HttpStatus.NOT_FOUND, "COURSE-0404", "존재하지 않는 코스입니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
