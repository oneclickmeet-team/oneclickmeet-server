package com.hsu.oneclickmeet.domain.notification.controller;

import com.hsu.oneclickmeet.domain.notification.dto.response.NotificationListResponse;
import com.hsu.oneclickmeet.domain.notification.dto.response.NotificationResponse;
import com.hsu.oneclickmeet.domain.notification.service.NotificationService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;
import com.hsu.oneclickmeet.global.response.ApiResponse;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
public class NotificationController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final String PAGE_SIZE_PATTERN = "0*(?:[1-9][0-9]?|100)";
	private static final String POSITIVE_ID_PATTERN = "0*[1-9][0-9]*";

	private final NotificationService notificationService;

	@GetMapping
	public ApiResponse<NotificationListResponse> list(@AuthenticationPrincipal Long memberId,
			@RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "size", required = false)
			@Pattern(regexp = PAGE_SIZE_PATTERN, message = "size는 1 이상 100 이하여야 합니다.") String size) {
		int pageSize = size == null ? DEFAULT_PAGE_SIZE : Integer.parseInt(size);
		return ApiResponse.success(notificationService.list(requireMemberId(memberId), cursor, pageSize));
	}

	@PatchMapping("/{notificationId}/read")
	public ApiResponse<NotificationResponse> markRead(@AuthenticationPrincipal Long memberId,
			@PathVariable("notificationId")
			@Pattern(regexp = POSITIVE_ID_PATTERN, message = "notificationId는 양의 정수여야 합니다.") String notificationId) {
		long id;
		try {
			id = Long.parseLong(notificationId);
		} catch (NumberFormatException exception) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
		return ApiResponse.success(notificationService.markRead(requireMemberId(memberId), id));
	}

	private long requireMemberId(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
		}
		return memberId;
	}
}
