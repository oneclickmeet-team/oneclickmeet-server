package com.hsu.oneclickmeet.domain.comment.exception;

import com.hsu.oneclickmeet.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommentErrorCode implements ErrorCode {
	NOT_AUTHOR(HttpStatus.FORBIDDEN, "COMMENT-0403", "본인이 작성한 첨언만 변경할 수 있습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT-0404", "첨언을 찾을 수 없습니다."),
	IDEMPOTENCY_MISMATCH(HttpStatus.CONFLICT, "COMMENT-0409-01", "같은 멱등성 키로 다른 요청을 보낼 수 없습니다."),
	REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "COMMENT-0409-02", "같은 요청을 처리하고 있습니다."),
	ARCHIVED_READ_ONLY(HttpStatus.CONFLICT, "COMMENT-0409-03", "개인 사본에 보존된 첨언은 변경할 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
