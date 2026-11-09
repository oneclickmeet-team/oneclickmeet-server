package com.hsu.oneclickmeet.domain.place.controller;

import com.hsu.oneclickmeet.domain.place.dto.response.NearbyPlacesResponse;
import com.hsu.oneclickmeet.domain.place.service.NearbyCategory;
import com.hsu.oneclickmeet.domain.place.service.NearbyPlaceService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;
import com.hsu.oneclickmeet.global.response.ApiResponse;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/saved-places", produces = MediaType.APPLICATION_JSON_VALUE)
public class NearbyPlaceController {

	private static final String POSITIVE_ID_PATTERN = "0*[1-9][0-9]*";

	private final NearbyPlaceService nearbyPlaceService;

	@GetMapping("/{savedPlaceId}/nearby")
	public ApiResponse<NearbyPlacesResponse> nearby(@AuthenticationPrincipal Long memberId,
			@PathVariable("savedPlaceId")
			@Pattern(regexp = POSITIVE_ID_PATTERN, message = "savedPlaceId는 양의 정수여야 합니다.") String savedPlaceId,
			@RequestParam(name = "category", defaultValue = "CAFE") NearbyCategory category) {
		if (memberId == null || memberId <= 0) {
			throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
		}
		long id;
		try {
			id = Long.parseLong(savedPlaceId);
		} catch (NumberFormatException exception) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
		return ApiResponse.success(nearbyPlaceService.nearby(memberId, id, category));
	}
}
