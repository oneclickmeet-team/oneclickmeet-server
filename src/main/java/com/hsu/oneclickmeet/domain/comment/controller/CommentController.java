package com.hsu.oneclickmeet.domain.comment.controller;

import com.hsu.oneclickmeet.domain.comment.dto.request.CreateCommentRequest;
import com.hsu.oneclickmeet.domain.comment.dto.request.UpdateCommentRequest;
import com.hsu.oneclickmeet.domain.comment.dto.response.CommentListResponse;
import com.hsu.oneclickmeet.domain.comment.dto.response.CommentResponse;
import com.hsu.oneclickmeet.domain.comment.entity.CommentTargetType;
import com.hsu.oneclickmeet.domain.comment.service.CommentService;
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
@RequestMapping(value = "/api/v1/comments", produces = MediaType.APPLICATION_JSON_VALUE)
public class CommentController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final String PAGE_SIZE_PATTERN = "0*(?:[1-9][0-9]?|100)";
	private static final String POSITIVE_ID_PATTERN = "0*[1-9][0-9]*";
	private static final String UUID_PATTERN = "[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}";

	private final CommentService commentService;

	@GetMapping
	public ApiResponse<CommentListResponse> list(@AuthenticationPrincipal Long memberId,
			@RequestParam("targetType")
			@Pattern(regexp = "COURSE|SHARED_COURSE", message = "targetType은 COURSE 또는 SHARED_COURSE여야 합니다.")
			String targetType,
			@RequestParam("targetId")
			@Pattern(regexp = POSITIVE_ID_PATTERN, message = "targetId는 양의 정수여야 합니다.") String targetId,
			@RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "size", required = false)
			@Pattern(regexp = PAGE_SIZE_PATTERN, message = "size는 1 이상 100 이하여야 합니다.") String size) {
		int pageSize = size == null ? DEFAULT_PAGE_SIZE : Integer.parseInt(size);
		return ApiResponse.success(commentService.list(requireMemberId(memberId), CommentTargetType.valueOf(targetType),
				parseId(targetId), cursor, pageSize));
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<CommentResponse> create(@AuthenticationPrincipal Long memberId,
			@RequestHeader("Idempotency-Key")
			@Pattern(regexp = UUID_PATTERN, message = "멱등성 키는 UUID여야 합니다.") String idempotencyKey,
			@Valid @RequestBody CreateCommentRequest request) {
		return ApiResponse.success(commentService.create(requireMemberId(memberId), request.targetId(),
				request.content(), UUID.fromString(idempotencyKey)));
	}

	@PatchMapping(value = "/{commentId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<CommentResponse> update(@AuthenticationPrincipal Long memberId,
			@PathVariable("commentId")
			@Pattern(regexp = POSITIVE_ID_PATTERN, message = "commentId는 양의 정수여야 합니다.") String commentId,
			@Valid @RequestBody UpdateCommentRequest request) {
		return ApiResponse.success(commentService.update(requireMemberId(memberId), parseId(commentId), request.content()));
	}

	@DeleteMapping("/{commentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Long memberId,
			@PathVariable("commentId")
			@Pattern(regexp = POSITIVE_ID_PATTERN, message = "commentId는 양의 정수여야 합니다.") String commentId) {
		commentService.delete(requireMemberId(memberId), parseId(commentId));
	}

	private long requireMemberId(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
		}
		return memberId;
	}

	private long parseId(String id) {
		try {
			return Long.parseLong(id);
		} catch (NumberFormatException exception) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
	}
}
