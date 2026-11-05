package com.hsu.oneclickmeet.domain.notification.exception;

import com.hsu.oneclickmeet.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum NotificationErrorCode implements ErrorCode {
	NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION-0404", "알림을 찾을 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
