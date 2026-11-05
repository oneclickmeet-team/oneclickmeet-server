package com.hsu.oneclickmeet.domain.place.controller;

import com.hsu.oneclickmeet.domain.place.dto.request.PlaceSearchRequest;
import com.hsu.oneclickmeet.domain.place.dto.response.PlaceSearchResponse;
import com.hsu.oneclickmeet.domain.place.service.PlaceSearchService;
import com.hsu.oneclickmeet.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
public class PlaceSearchController {

	private final PlaceSearchService placeSearchService;

	@GetMapping("/search")
	public ApiResponse<PlaceSearchResponse> search(@Valid @ModelAttribute PlaceSearchRequest request) {
		return ApiResponse.success(placeSearchService.search(request));
	}
}
