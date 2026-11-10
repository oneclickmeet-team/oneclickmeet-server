package com.hsu.oneclickmeet.domain.place.controller;

import com.hsu.oneclickmeet.domain.place.client.RecognitionImage;
import com.hsu.oneclickmeet.domain.place.dto.request.ResolveLinkRequest;
import com.hsu.oneclickmeet.domain.place.dto.response.PlaceResolveResponse;
import com.hsu.oneclickmeet.domain.place.service.PlaceResolveService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;
import com.hsu.oneclickmeet.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/places/resolve", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PlaceResolveController {

	private final PlaceResolveService placeResolveService;

	@PostMapping(value = "/link", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<PlaceResolveResponse> resolveLink(@Valid @RequestBody ResolveLinkRequest request) {
		return ApiResponse.success(placeResolveService.resolveLink(request.sharedText().trim()));
	}

	@PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<PlaceResolveResponse> resolveImage(@RequestParam("image") MultipartFile image,
			@RequestParam(name = "extraImages", required = false) List<MultipartFile> extraImages) {
		List<MultipartFile> extras = extraImages == null ? List.of() : extraImages;
		try {
			List<RecognitionImage> extraFiles = new ArrayList<>();
			for (MultipartFile extra : extras) {
				extraFiles.add(toRecognitionImage(extra));
			}
			return ApiResponse.success(placeResolveService.resolveImage(toRecognitionImage(image), extraFiles));
		} catch (IOException exception) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
	}

	private RecognitionImage toRecognitionImage(MultipartFile file) throws IOException {
		return new RecognitionImage(file.getBytes(), file.getContentType());
	}
}
