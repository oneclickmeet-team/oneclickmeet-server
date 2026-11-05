package com.hsu.oneclickmeet.domain.place.exception;

import com.hsu.oneclickmeet.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum PlaceErrorCode implements ErrorCode {
	NOT_FOUND(HttpStatus.NOT_FOUND, "PLACE-0404", "장소를 찾을 수 없습니다."),
	UPSTREAM_FAILURE(HttpStatus.BAD_GATEWAY, "PLACE-0502", "장소 정보를 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
