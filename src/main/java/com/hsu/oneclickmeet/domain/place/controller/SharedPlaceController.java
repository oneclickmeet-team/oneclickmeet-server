package com.hsu.oneclickmeet.domain.place.controller;

import com.hsu.oneclickmeet.domain.place.dto.response.SharedPlaceListResponse;
import com.hsu.oneclickmeet.domain.place.dto.response.SharedPlaceResponse;
import com.hsu.oneclickmeet.domain.place.service.SharedPlaceService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;
import com.hsu.oneclickmeet.global.response.ApiResponse;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class SharedPlaceController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final String PAGE_SIZE_PATTERN = "0*(?:[1-9][0-9]?|100)";

	private final SharedPlaceService sharedPlaceService;

	@PostMapping("/saved-places/{savedPlaceId}/share")
	public ResponseEntity<ApiResponse<SharedPlaceResponse>> share(@AuthenticationPrincipal Long memberId,
			@PathVariable("savedPlaceId") @Positive(message = "savedPlaceId는 양의 정수여야 합니다.") long savedPlaceId) {
		var result = sharedPlaceService.share(requireMemberId(memberId), savedPlaceId);
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(ApiResponse.success(result.place()));
	}

	@GetMapping("/couples/me/places")
	public ApiResponse<SharedPlaceListResponse> list(@AuthenticationPrincipal Long memberId,
			@RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "size", required = false)
			@Pattern(regexp = PAGE_SIZE_PATTERN, message = "size는 1 이상 100 이하여야 합니다.") String size) {
		int pageSize = size == null ? DEFAULT_PAGE_SIZE : Integer.parseInt(size);
		return ApiResponse.success(sharedPlaceService.list(requireMemberId(memberId), cursor, pageSize));
	}

	private long requireMemberId(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
		}
		return memberId;
	}
}
