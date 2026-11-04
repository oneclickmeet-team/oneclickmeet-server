package com.hsu.oneclickmeet.domain.course.controller;

import com.hsu.oneclickmeet.domain.course.dto.response.CourseShareResponse;
import com.hsu.oneclickmeet.domain.course.service.CourseShareService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;
import com.hsu.oneclickmeet.global.response.ApiResponse;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CourseShareController {

	private static final String UUID_PATTERN = "[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}";

	private final CourseShareService courseShareService;

	@PostMapping(value = "/api/v1/courses/{courseId}/shares", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<CourseShareResponse> share(@AuthenticationPrincipal Long memberId,
			@PathVariable("courseId")
			@Pattern(regexp = "0*[1-9][0-9]*", message = "courseId는 양의 정수여야 합니다.") String courseId,
			@RequestHeader("Idempotency-Key")
			@Pattern(regexp = UUID_PATTERN, message = "멱등성 키는 UUID여야 합니다.") String idempotencyKey) {
		if (memberId == null || memberId <= 0) {
			throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
		}
		return ApiResponse.success(courseShareService.share(memberId, parseCourseId(courseId), UUID.fromString(idempotencyKey)));
	}

	private long parseCourseId(String courseId) {
		try {
			return Long.parseLong(courseId);
		} catch (NumberFormatException exception) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
	}
}
