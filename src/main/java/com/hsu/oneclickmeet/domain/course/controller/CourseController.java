package com.hsu.oneclickmeet.domain.course.controller;

import com.hsu.oneclickmeet.domain.course.dto.request.UpdateCourseRequest;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseDetailResponse;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseListResponse;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseUpdateResponse;
import com.hsu.oneclickmeet.domain.course.service.CourseDeleteService;
import com.hsu.oneclickmeet.domain.course.service.CourseReadService;
import com.hsu.oneclickmeet.domain.course.service.CourseUpdateService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/courses", produces = MediaType.APPLICATION_JSON_VALUE)
public class CourseController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final String PAGE_SIZE_PATTERN = "0*(?:[1-9][0-9]?|100)";
	private static final String COURSE_ID_PATTERN = "0*[1-9][0-9]*";
	private static final String UUID_PATTERN = "[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}";

	private final CourseReadService courseReadService;
	private final CourseUpdateService courseUpdateService;
	private final CourseDeleteService courseDeleteService;

	@GetMapping
	public ApiResponse<CourseListResponse> listCourses(@AuthenticationPrincipal Long memberId,
			@RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "size", required = false)
			@Pattern(regexp = PAGE_SIZE_PATTERN, message = "size는 1 이상 100 이하여야 합니다.") String size) {
		int pageSize = size == null ? DEFAULT_PAGE_SIZE : Integer.parseInt(size);
		return ApiResponse.success(courseReadService.list(requireMemberId(memberId), cursor, pageSize));
	}

	@GetMapping("/{courseId}")
	public ApiResponse<CourseDetailResponse> getCourse(@AuthenticationPrincipal Long memberId,
			@PathVariable("courseId")
			@Pattern(regexp = COURSE_ID_PATTERN, message = "courseId는 양의 정수여야 합니다.") String courseId) {
		return ApiResponse.success(courseReadService.detail(requireMemberId(memberId), parseCourseId(courseId)));
	}

	@PutMapping(value = "/{courseId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<CourseUpdateResponse> updateCourse(@AuthenticationPrincipal Long memberId,
			@PathVariable("courseId")
			@Pattern(regexp = COURSE_ID_PATTERN, message = "courseId는 양의 정수여야 합니다.") String courseId,
			@RequestHeader("Idempotency-Key") @Pattern(regexp = UUID_PATTERN, message = "멱등성 키는 UUID여야 합니다.")
			String idempotencyKey, @Valid @RequestBody UpdateCourseRequest request) {
		return ApiResponse.success(courseUpdateService.update(requireMemberId(memberId), parseCourseId(courseId),
				UUID.fromString(idempotencyKey), request));
	}

	@DeleteMapping("/{courseId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteCourse(@AuthenticationPrincipal Long memberId,
			@PathVariable("courseId")
			@Pattern(regexp = COURSE_ID_PATTERN, message = "courseId는 양의 정수여야 합니다.") String courseId) {
		courseDeleteService.delete(requireMemberId(memberId), parseCourseId(courseId));
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
