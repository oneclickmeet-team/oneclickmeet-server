package com.hsu.oneclickmeet.domain.course.controller;

import com.hsu.oneclickmeet.domain.course.dto.request.UpdateSharedCourseRequest;
import com.hsu.oneclickmeet.domain.course.dto.response.SharedCourseDetailResponse;
import com.hsu.oneclickmeet.domain.course.dto.response.SharedCourseListResponse;
import com.hsu.oneclickmeet.domain.course.dto.response.SharedCourseUpdateResponse;
import com.hsu.oneclickmeet.domain.course.service.SharedCourseCancellationService;
import com.hsu.oneclickmeet.domain.course.service.SharedCourseReadService;
import com.hsu.oneclickmeet.domain.course.service.SharedCourseUpdateService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;
import com.hsu.oneclickmeet.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class SharedCourseController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final String PAGE_SIZE_PATTERN = "0*(?:[1-9][0-9]?|100)";
	private static final String UUID_PATTERN = "[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}";

	private final SharedCourseReadService sharedCourseReadService;
	private final SharedCourseUpdateService sharedCourseUpdateService;
	private final SharedCourseCancellationService sharedCourseCancellationService;

	@GetMapping("/couples/me/courses")
	public ApiResponse<SharedCourseListResponse> listCourses(@AuthenticationPrincipal Long memberId,
			@RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "size", required = false)
			@Pattern(regexp = PAGE_SIZE_PATTERN, message = "size는 1 이상 100 이하여야 합니다.") String size) {
		int pageSize = size == null ? DEFAULT_PAGE_SIZE : Integer.parseInt(size);
		return ApiResponse.success(sharedCourseReadService.list(requireMemberId(memberId), cursor, pageSize));
	}

	@GetMapping("/shared-courses/{sharedCourseId}")
	public ApiResponse<SharedCourseDetailResponse> getCourse(@AuthenticationPrincipal Long memberId,
			@PathVariable("sharedCourseId")
			@Pattern(regexp = "0*[1-9][0-9]*", message = "sharedCourseId는 양의 정수여야 합니다.") String sharedCourseId) {
		return ApiResponse.success(sharedCourseReadService.detail(requireMemberId(memberId), parseCourseId(sharedCourseId)));
	}

	@PatchMapping(value = "/shared-courses/{sharedCourseId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<SharedCourseUpdateResponse> updateCourse(@AuthenticationPrincipal Long memberId,
			@PathVariable("sharedCourseId")
			@Pattern(regexp = "0*[1-9][0-9]*", message = "sharedCourseId는 양의 정수여야 합니다.") String sharedCourseId,
			@RequestHeader("Idempotency-Key")
			@Pattern(regexp = UUID_PATTERN, message = "멱등성 키는 UUID여야 합니다.") String idempotencyKey,
			@Valid @RequestBody UpdateSharedCourseRequest request) {
		return ApiResponse.success(sharedCourseUpdateService.update(requireMemberId(memberId),
				parseCourseId(sharedCourseId), UUID.fromString(idempotencyKey), request));
	}

	@DeleteMapping("/shared-courses/{sharedCourseId}")
	public ResponseEntity<Void> cancelSharing(@AuthenticationPrincipal Long memberId,
			@PathVariable("sharedCourseId")
			@Pattern(regexp = "0*[1-9][0-9]*", message = "sharedCourseId는 양의 정수여야 합니다.") String sharedCourseId) {
		sharedCourseCancellationService.cancel(requireMemberId(memberId), parseCourseId(sharedCourseId));
		return ResponseEntity.noContent().build();
	}

	private long parseCourseId(String courseId) {
		try {
			return Long.parseLong(courseId);
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
