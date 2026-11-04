package com.hsu.oneclickmeet.domain.couple.exception;

import com.hsu.oneclickmeet.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CoupleErrorCode implements ErrorCode {
	STATE_CONFLICT(HttpStatus.CONFLICT, "COUPLE-0409-02", "현재 관계 상태에서는 요청을 처리할 수 없습니다."),
	IDEMPOTENCY_MISMATCH(HttpStatus.CONFLICT, "COUPLE-0409-03", "같은 멱등성 키로 다른 요청을 보낼 수 없습니다."),
	REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "COUPLE-0409-04", "같은 요청을 처리하고 있습니다."),
	INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "INVITATION-0404", "초대 코드를 찾을 수 없습니다."),
	INVITATION_UNAVAILABLE(HttpStatus.CONFLICT, "INVITATION-0409", "이미 사용했거나 폐기된 초대 코드입니다."),
	INVITATION_EXPIRED(HttpStatus.GONE, "INVITATION-0410", "초대 코드가 만료되었습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
