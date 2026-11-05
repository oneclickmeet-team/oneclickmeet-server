package com.hsu.oneclickmeet.domain.recommendation.controller;

import com.hsu.oneclickmeet.domain.recommendation.dto.request.RecommendCoursesRequest;
import com.hsu.oneclickmeet.domain.recommendation.dto.response.CourseRecommendationResponse;
import com.hsu.oneclickmeet.domain.recommendation.service.CourseRecommendationService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;
import com.hsu.oneclickmeet.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/recommendations", produces = MediaType.APPLICATION_JSON_VALUE)
public class CourseRecommendationController {

	private final CourseRecommendationService recommendationService;

	@PostMapping(value = "/courses", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<CourseRecommendationResponse> recommend(@AuthenticationPrincipal Long memberId,
			@Valid @RequestBody RecommendCoursesRequest request) {
		if (memberId == null || memberId <= 0) {
			throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
		}
		return ApiResponse.success(recommendationService.recommend(memberId, request));
	}
}
