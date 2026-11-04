package com.hsu.oneclickmeet.domain.couple.controller;

import com.hsu.oneclickmeet.domain.couple.dto.request.ConnectCoupleRequest;
import com.hsu.oneclickmeet.domain.couple.dto.request.IssueInvitationRequest;
import com.hsu.oneclickmeet.domain.couple.dto.response.ConnectionResponse;
import com.hsu.oneclickmeet.domain.couple.dto.response.InvitationResponse;
import com.hsu.oneclickmeet.domain.couple.dto.response.MyCoupleResponse;
import com.hsu.oneclickmeet.domain.couple.dto.response.MyInvitationResponse;
import com.hsu.oneclickmeet.domain.couple.entity.InvitationPurpose;
import com.hsu.oneclickmeet.domain.couple.service.CoupleService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;
import com.hsu.oneclickmeet.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class CoupleController {

	private static final String UUID_PATTERN = "[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}";

	private final CoupleService coupleService;

	@GetMapping("/couples/me")
	public ApiResponse<MyCoupleResponse> getMyCouple(@AuthenticationPrincipal Long memberId) {
		return ApiResponse.success(coupleService.getMyCouple(requireMemberId(memberId)));
	}

	@PostMapping(value = "/couple-invitations", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<InvitationResponse> issueInvitation(@AuthenticationPrincipal Long memberId,
			@Valid @RequestBody IssueInvitationRequest request) {
		return ApiResponse.success(coupleService.issueInvitation(requireMemberId(memberId),
			InvitationPurpose.valueOf(request.purpose())));
	}

	@GetMapping("/couple-invitations/me")
	public ApiResponse<MyInvitationResponse> getMyInvitation(@AuthenticationPrincipal Long memberId,
			@RequestParam("purpose") InvitationPurpose purpose) {
		return ApiResponse.success(coupleService.getMyInvitation(requireMemberId(memberId), purpose));
	}

	@PostMapping(value = "/couple-connections", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<ConnectionResponse> connect(@AuthenticationPrincipal Long memberId,
			@RequestHeader("Idempotency-Key") @Pattern(regexp = UUID_PATTERN, message = "멱등성 키는 UUID여야 합니다.")
			String idempotencyKey, @Valid @RequestBody ConnectCoupleRequest request) {
		return ApiResponse.success(coupleService.connect(requireMemberId(memberId), UUID.fromString(idempotencyKey),
			request.invitationCode()));
	}

	private long requireMemberId(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
		}
		return memberId;
	}
}
