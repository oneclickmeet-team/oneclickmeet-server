package com.hsu.oneclickmeet.domain.memo.exception;

import com.hsu.oneclickmeet.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemoErrorCode implements ErrorCode {
	NOT_FOUND(HttpStatus.NOT_FOUND, "MEMO-0404", "메모를 찾을 수 없습니다."),
	PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "PLACE-0404", "장소를 찾을 수 없습니다."),
	ALREADY_EXISTS(HttpStatus.CONFLICT, "MEMO-0409", "이미 이 대상에 작성한 메모가 있습니다."),
	IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "MEMO-0409-01", "이미 다른 요청에 사용한 멱등성 키입니다."),
	CREATE_IN_PROGRESS(HttpStatus.CONFLICT, "MEMO-0409-02", "같은 멱등성 키의 메모 작성 요청을 처리 중입니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
