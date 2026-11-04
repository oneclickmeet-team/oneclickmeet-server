package com.hsu.oneclickmeet.domain.memo.controller;

import com.hsu.oneclickmeet.domain.memo.dto.request.CreateMemoRequest;
import com.hsu.oneclickmeet.domain.memo.dto.request.UpdateMemoRequest;
import com.hsu.oneclickmeet.domain.memo.dto.response.MemoListResponse;
import com.hsu.oneclickmeet.domain.memo.dto.response.MemoResponse;
import com.hsu.oneclickmeet.domain.memo.entity.MemoTargetType;
import com.hsu.oneclickmeet.domain.memo.service.MemoService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;
import com.hsu.oneclickmeet.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
@RequestMapping(value = "/api/v1/memos", produces = MediaType.APPLICATION_JSON_VALUE)
public class MemoController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final String PAGE_SIZE_PATTERN = "0*(?:[1-9][0-9]?|100)";
	private static final String POSITIVE_ID_PATTERN = "0*[1-9][0-9]*";
	private static final String UUID_PATTERN = "[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}";

	private final MemoService memoService;

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<MemoResponse> create(@AuthenticationPrincipal Long memberId,
			@RequestHeader("Idempotency-Key") @Pattern(regexp = UUID_PATTERN, message = "멱등성 키는 UUID여야 합니다.")
			String idempotencyKey, @Valid @RequestBody CreateMemoRequest request) {
		return ApiResponse.success(memoService.create(requireMemberId(memberId), UUID.fromString(idempotencyKey), request));
	}

	@GetMapping
	public ApiResponse<MemoListResponse> list(@AuthenticationPrincipal Long memberId,
			@RequestParam(name = "targetType", required = false) String targetType,
			@RequestParam(name = "targetId", required = false)
			@Pattern(regexp = POSITIVE_ID_PATTERN, message = "targetId는 양의 정수여야 합니다.") String targetId,
			@RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "size", required = false)
			@Pattern(regexp = PAGE_SIZE_PATTERN, message = "size는 1 이상 100 이하여야 합니다.") String size) {
		long authorId = requireMemberId(memberId);
		if ((targetType == null) != (targetId == null)) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
		MemoTargetType type = targetType == null ? null : parseTargetType(targetType);
		Long id = targetId == null ? null : parseId(targetId);
		int pageSize = size == null ? DEFAULT_PAGE_SIZE : Integer.parseInt(size);
		return ApiResponse.success(memoService.list(authorId, type, id, cursor, pageSize));
	}

	@GetMapping("/{memoId}")
	public ApiResponse<MemoResponse> detail(@AuthenticationPrincipal Long memberId,
			@PathVariable("memoId")
			@Pattern(regexp = POSITIVE_ID_PATTERN, message = "memoId는 양의 정수여야 합니다.") String memoId) {
		return ApiResponse.success(memoService.detail(requireMemberId(memberId), parseId(memoId)));
	}

	@PatchMapping(value = "/{memoId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<MemoResponse> update(@AuthenticationPrincipal Long memberId,
			@PathVariable("memoId")
			@Pattern(regexp = POSITIVE_ID_PATTERN, message = "memoId는 양의 정수여야 합니다.") String memoId,
			@Valid @RequestBody UpdateMemoRequest request) {
		return ApiResponse.success(memoService.update(requireMemberId(memberId), parseId(memoId), request.content()));
	}

	@DeleteMapping("/{memoId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Long memberId,
			@PathVariable("memoId")
			@Pattern(regexp = POSITIVE_ID_PATTERN, message = "memoId는 양의 정수여야 합니다.") String memoId) {
		memoService.delete(requireMemberId(memberId), parseId(memoId));
	}

	private MemoTargetType parseTargetType(String targetType) {
		try {
			return MemoTargetType.valueOf(targetType);
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
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
