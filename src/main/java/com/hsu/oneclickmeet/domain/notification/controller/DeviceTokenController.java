package com.hsu.oneclickmeet.domain.notification.controller;

import com.hsu.oneclickmeet.domain.notification.dto.request.RegisterDeviceRequest;
import com.hsu.oneclickmeet.domain.notification.dto.request.UnregisterDeviceRequest;
import com.hsu.oneclickmeet.domain.notification.service.DeviceTokenService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/devices", produces = MediaType.APPLICATION_JSON_VALUE)
public class DeviceTokenController {

	private final DeviceTokenService deviceTokenService;

	/** 푸시 기기 토큰 등록(앱 실행·로그인 때, 토큰이 바뀔 때). 같은 토큰을 다시 보내도 안전하다. */
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void register(@AuthenticationPrincipal Long memberId, @Valid @RequestBody RegisterDeviceRequest request) {
		deviceTokenService.register(requireMemberId(memberId), request.platform(), request.token());
	}

	/** 푸시 기기 토큰 해제(로그아웃 때). 본문으로 토큰을 받아 URL·로그에 토큰이 남지 않게 한다. */
	@PostMapping(value = "/unregister", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unregister(@AuthenticationPrincipal Long memberId,
			@Valid @RequestBody UnregisterDeviceRequest request) {
		deviceTokenService.unregister(requireMemberId(memberId), request.token());
	}

	private long requireMemberId(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
		}
		return memberId;
	}
}
