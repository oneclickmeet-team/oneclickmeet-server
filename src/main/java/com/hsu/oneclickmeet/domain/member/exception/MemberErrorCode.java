package com.hsu.oneclickmeet.domain.member.exception;

import com.hsu.oneclickmeet.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum MemberErrorCode implements ErrorCode {
	INVALID_SOCIAL_TOKEN(HttpStatus.UNAUTHORIZED, "MEMBER-0401", "소셜 로그인 정보를 확인할 수 없어요. 다시 로그인해 주세요."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "MEMBER-0402", "로그인이 만료되었어요. 다시 로그인해 주세요."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER-0404", "회원을 찾을 수 없습니다."),
	SOCIAL_LOGIN_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "MEMBER-0502", "소셜 로그인을 잠시 사용할 수 없어요. 잠시 후 다시 시도해 주세요.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
