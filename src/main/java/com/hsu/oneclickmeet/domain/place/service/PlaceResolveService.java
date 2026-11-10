package com.hsu.oneclickmeet.domain.place.service;

import com.hsu.oneclickmeet.domain.place.client.PlaceCandidate;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchClient;
import com.hsu.oneclickmeet.domain.place.client.PlaceRecognizer;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchCommand;
import com.hsu.oneclickmeet.domain.place.client.Recognition;
import com.hsu.oneclickmeet.domain.place.client.RecognitionImage;
import com.hsu.oneclickmeet.domain.place.dto.response.ResolvedPlaceResponse;
import com.hsu.oneclickmeet.domain.place.dto.response.PlaceResolveResponse;
import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 링크·이미지로 장소 후보 찾기. AI 서버가 이름 추출부터 카카오 검색·매칭까지 마친 후보를 돌려주므로 그대로 쓴다.
 * AI 서버를 쓸 수 없으면 링크는 규칙으로 뽑은 이름을 카카오에서 검색하고, 이미지는 직접 검색으로 안내한다.
 * 후보가 없으면 오류 대신 NEEDS_SEARCH로 응답한다. 폴백의 카카오 검색 자체가 실패하면 그대로 502로 응답한다.
 * 사용자가 보낸 텍스트·이미지와 추출된 이름은 로그에 남기지 않는다.
 */
@Slf4j
@Service
public class PlaceResolveService {

	static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
	static final int MAX_EXTRA_IMAGES = 4;
	static final int NAMES_TO_SEARCH = 3;
	static final int CANDIDATES_PER_NAME = 5;
	static final int MAX_CANDIDATES = 10;
	private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

	private final PlaceRecognizer recognizer;
	private final PlaceSearchClient searchClient;

	public PlaceResolveService(PlaceRecognizer recognizer, PlaceSearchClient searchClient) {
		this.recognizer = recognizer;
		this.searchClient = searchClient;
	}

	public PlaceResolveResponse resolveLink(String sharedText) {
		try {
			return toResponse(recognizer.recognizeText(sharedText));
		} catch (PlaceUpstreamException exception) {
			log.warn("AI 서버를 쓸 수 없어 규칙으로 장소 이름을 찾습니다. reason={}", exception.getReason());
			return PlaceResolveResponse.of(null, candidatesFor(SharedTextNameHeuristic.extract(sharedText)));
		}
	}

	public PlaceResolveResponse resolveImage(RecognitionImage image, List<RecognitionImage> extraImages) {
		validateImage(image);
		if (extraImages.size() > MAX_EXTRA_IMAGES) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
		extraImages.forEach(this::validateImage);
		try {
			return toResponse(recognizer.recognizeImage(image, extraImages));
		} catch (PlaceUpstreamException exception) {
			log.warn("AI 서버를 쓸 수 없어 직접 검색으로 안내합니다. reason={}", exception.getReason());
			return PlaceResolveResponse.needsSearch(null);
		}
	}

	private PlaceResolveResponse toResponse(Recognition recognition) {
		return PlaceResolveResponse.of(recognition.extractedQuery(),
				recognition.places().stream().map(ResolvedPlaceResponse::from).toList());
	}

	private List<ResolvedPlaceResponse> candidatesFor(List<String> names) {
		Map<String, PlaceCandidate> unique = new LinkedHashMap<>();
		for (String name : names.stream().limit(NAMES_TO_SEARCH).toList()) {
			for (PlaceCandidate candidate : searchClient
					.search(new PlaceSearchCommand(name, 1, CANDIDATES_PER_NAME, null, null)).candidates()) {
				unique.putIfAbsent(candidate.providerPlaceId(), candidate);
			}
		}
		List<ResolvedPlaceResponse> result = new ArrayList<>();
		for (PlaceCandidate candidate : unique.values()) {
			if (result.size() == MAX_CANDIDATES) {
				break;
			}
			result.add(ResolvedPlaceResponse.from(candidate));
		}
		return result;
	}

	private void validateImage(RecognitionImage file) {
		byte[] image = file == null ? null : file.bytes();
		String contentType = file == null ? null : file.contentType();
		if (image == null || image.length == 0 || image.length > MAX_IMAGE_BYTES
				|| contentType == null || !IMAGE_TYPES.contains(contentType.toLowerCase())
				|| !matchesSignature(image, contentType.toLowerCase())) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
	}

	/** 선언한 형식과 파일 앞부분이 맞는지 확인한다(이름만 이미지인 파일을 AI 서버로 보내지 않기 위함). */
	private boolean matchesSignature(byte[] image, String contentType) {
		return switch (contentType) {
			case "image/jpeg" -> image.length > 3 && (image[0] & 0xFF) == 0xFF && (image[1] & 0xFF) == 0xD8
					&& (image[2] & 0xFF) == 0xFF;
			case "image/png" -> image.length > 8 && (image[0] & 0xFF) == 0x89 && image[1] == 'P'
					&& image[2] == 'N' && image[3] == 'G';
			case "image/webp" -> image.length > 12 && image[0] == 'R' && image[1] == 'I' && image[2] == 'F'
					&& image[3] == 'F' && image[8] == 'W' && image[9] == 'E' && image[10] == 'B'
					&& image[11] == 'P';
			default -> false;
		};
	}
}
