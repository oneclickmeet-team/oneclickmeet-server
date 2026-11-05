package com.hsu.oneclickmeet.domain.place.controller;

import com.hsu.oneclickmeet.domain.place.dto.request.SaveSavedPlaceRequest;
import com.hsu.oneclickmeet.domain.place.dto.response.SavedPlaceListResponse;
import com.hsu.oneclickmeet.domain.place.dto.response.SavedPlaceResponse;
import com.hsu.oneclickmeet.domain.place.service.SavedPlaceService;
import com.hsu.oneclickmeet.domain.place.service.SavedPlaceService.SaveResult;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;
import com.hsu.oneclickmeet.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/saved-places", produces = MediaType.APPLICATION_JSON_VALUE)
public class SavedPlaceController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final String PAGE_SIZE_PATTERN = "0*(?:[1-9][0-9]?|100)";
	private static final String POSITIVE_ID_PATTERN = "0*[1-9][0-9]*";

	private final SavedPlaceService savedPlaceService;

	/** 새로 저장하면 201, 이미 저장한 장소면 기존 항목과 함께 200을 돌려준다. */
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<SavedPlaceResponse>> save(@AuthenticationPrincipal Long memberId,
			@Valid @RequestBody SaveSavedPlaceRequest request) {
		SaveResult result = savedPlaceService.save(requireMemberId(memberId), request);
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(ApiResponse.success(result.place()));
	}

	@GetMapping
	public ApiResponse<SavedPlaceListResponse> list(@AuthenticationPrincipal Long memberId,
			@RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "size", required = false)
			@Pattern(regexp = PAGE_SIZE_PATTERN, message = "size는 1 이상 100 이하여야 합니다.") String size,
			@RequestParam(name = "categoryGroupCode", required = false)
			@Size(max = 10, message = "categoryGroupCode는 10자 이하여야 합니다.") String categoryGroupCode) {
		int pageSize = size == null ? DEFAULT_PAGE_SIZE : Integer.parseInt(size);
		return ApiResponse.success(
				savedPlaceService.list(requireMemberId(memberId), cursor, pageSize, categoryGroupCode));
	}

	@GetMapping("/{savedPlaceId}")
	public ApiResponse<SavedPlaceResponse> detail(@AuthenticationPrincipal Long memberId,
			@PathVariable("savedPlaceId")
			@Pattern(regexp = POSITIVE_ID_PATTERN, message = "savedPlaceId는 양의 정수여야 합니다.") String savedPlaceId) {
		return ApiResponse.success(savedPlaceService.detail(requireMemberId(memberId), parseId(savedPlaceId)));
	}

	@DeleteMapping("/{savedPlaceId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Long memberId,
			@PathVariable("savedPlaceId")
			@Pattern(regexp = POSITIVE_ID_PATTERN, message = "savedPlaceId는 양의 정수여야 합니다.") String savedPlaceId) {
		savedPlaceService.delete(requireMemberId(memberId), parseId(savedPlaceId));
	}

	private long parseId(String id) {
		try {
			return Long.parseLong(id);
		} catch (NumberFormatException exception) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
	}

	private long requireMemberId(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
		}
		return memberId;
	}
}
