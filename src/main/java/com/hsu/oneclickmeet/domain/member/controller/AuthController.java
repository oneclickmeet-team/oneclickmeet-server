package com.hsu.oneclickmeet.domain.member.controller;

import com.hsu.oneclickmeet.domain.member.dto.request.GoogleLoginRequest;
import com.hsu.oneclickmeet.domain.member.dto.request.KakaoLoginRequest;
import com.hsu.oneclickmeet.domain.member.dto.request.RefreshTokenRequest;
import com.hsu.oneclickmeet.domain.member.dto.response.LoginResponse;
import com.hsu.oneclickmeet.domain.member.dto.response.TokenResponse;
import com.hsu.oneclickmeet.domain.member.entity.SocialProvider;
import com.hsu.oneclickmeet.domain.member.service.AuthService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;
import com.hsu.oneclickmeet.global.response.ApiResponse;
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
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

	private final AuthService authService;

	@PostMapping(value = "/login/kakao", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<LoginResponse> loginWithKakao(@Valid @RequestBody KakaoLoginRequest request) {
		return ApiResponse.success(authService.login(SocialProvider.KAKAO, request.accessToken()));
	}

	@PostMapping(value = "/login/google", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<LoginResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
		return ApiResponse.success(authService.login(SocialProvider.GOOGLE, request.idToken()));
	}

	@PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return ApiResponse.success(authService.refresh(request.refreshToken()));
	}

	@PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@AuthenticationPrincipal Long memberId, @Valid @RequestBody RefreshTokenRequest request) {
		if (memberId == null || memberId <= 0) {
			throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
		}
		authService.logout(memberId, request.refreshToken());
	}
}
