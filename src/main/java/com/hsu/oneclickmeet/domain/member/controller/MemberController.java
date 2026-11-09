package com.hsu.oneclickmeet.domain.member.controller;

import com.hsu.oneclickmeet.domain.member.dto.request.UpdateMemberRequest;
import com.hsu.oneclickmeet.domain.member.dto.response.MemberResponse;
import com.hsu.oneclickmeet.domain.member.service.MemberService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;
import com.hsu.oneclickmeet.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/members", produces = MediaType.APPLICATION_JSON_VALUE)
public class MemberController {

	private final MemberService memberService;

	@GetMapping("/me")
	public ApiResponse<MemberResponse> me(@AuthenticationPrincipal Long memberId) {
		return ApiResponse.success(memberService.me(requireMemberId(memberId)));
	}

	@PatchMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<MemberResponse> update(@AuthenticationPrincipal Long memberId,
			@Valid @RequestBody UpdateMemberRequest request) {
		return ApiResponse.success(memberService.changeNickname(requireMemberId(memberId), request.nickname()));
	}

	private long requireMemberId(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
		}
		return memberId;
	}
}
