package com.hsu.oneclickmeet.domain.place.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hsu.oneclickmeet.domain.place.client.Recognition.RecognizedPlace;
import com.hsu.oneclickmeet.domain.place.client.Recognition.Tag;
import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException;
import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException.Reason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * AI 서버(FastAPI) 호출 클라이언트. 계약은 AI 파트의 API_CONVENTION(v2.1)을 따른다.
 * <pre>
 * POST /internal/v1/place-recognitions        {"sharedText": "..."}      -> RecognitionResponse
 * POST /internal/v1/place-image-recognitions  multipart "image" 파일      -> RecognitionResponse
 * </pre>
 * 인식은 외부 LLM 호출이 들어가 비용·시간이 크므로 재시도하지 않는다.
 * 사용자가 보낸 텍스트·이미지와 인식 결과는 로그에 남기지 않는다.
 * 카카오 장소 ID가 없거나 카카오가 아닌 제공자(DEMO 등)의 후보는 보관함에 저장할 수 없어 버린다.
 */
@Slf4j
@Component
public class FastApiPlaceRecognizer implements PlaceRecognizer {

	static final int MAX_CANDIDATES = 10;
	static final int MAX_TAGS = 5;
	/** 블로그 글이 이보다 적게 모인 가게의 태그는 근거가 약해 숨긴다(AI 파트 권장). */
	static final int MIN_TAG_POST_COUNT = 3;
	private static final String KAKAO = "KAKAO";

	private final RestClient restClient;
	private final AiServerProperties properties;

	public FastApiPlaceRecognizer(@Qualifier("aiServerRestClient") RestClient restClient,
			AiServerProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	@Override
	public Recognition recognizeText(String sharedText) {
		return call(() -> restClient.post()
				.uri("/internal/v1/place-recognitions")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("sharedText", sharedText))
				.retrieve()
				.body(RecognitionPayload.class));
	}

	@Override
	public Recognition recognizeImage(byte[] image, String contentType) {
		return call(() -> {
			HttpHeaders partHeaders = new HttpHeaders();
			partHeaders.setContentType(MediaType.parseMediaType(contentType));
			MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
			body.add("image", new HttpEntity<>(new ByteArrayResource(image) {
				@Override
				public String getFilename() {
					return "image";
				}
			}, partHeaders));
			return restClient.post()
					.uri("/internal/v1/place-image-recognitions")
					.contentType(MediaType.MULTIPART_FORM_DATA)
					.body(body)
					.retrieve()
					.body(RecognitionPayload.class);
		});
	}

	private Recognition call(Supplier<RecognitionPayload> request) {
		if (!properties.configured()) {
			throw new PlaceUpstreamException(Reason.AUTH, "AI 서버 주소가 설정되지 않았습니다.");
		}
		try {
			return toRecognition(request.get());
		} catch (HttpClientErrorException e) {
			int status = e.getStatusCode().value();
			Reason reason = status == 401 || status == 403 ? Reason.AUTH
					: status == 429 ? Reason.RATE_LIMIT : Reason.REQUEST_REJECTED;
			log.warn("AI 서버가 요청을 거부했습니다. status={}", status);
			throw new PlaceUpstreamException(reason, "AI 서버 요청 거부", e);
		} catch (HttpServerErrorException e) {
			log.warn("AI 서버 오류. status={}", e.getStatusCode().value());
			throw new PlaceUpstreamException(Reason.SERVER_ERROR, "AI 서버 오류", e);
		} catch (ResourceAccessException e) {
			log.warn("AI 서버 연결 실패 또는 시간 초과");
			throw new PlaceUpstreamException(Reason.NETWORK, "AI 서버 연결 실패", e);
		} catch (RestClientException e) {
			log.warn("AI 서버 응답을 읽을 수 없습니다.");
			throw new PlaceUpstreamException(Reason.BAD_RESPONSE, "AI 서버 응답 오류", e);
		}
	}

	private Recognition toRecognition(RecognitionPayload payload) {
		if (payload == null || payload.status() == null) {
			throw new PlaceUpstreamException(Reason.BAD_RESPONSE, "AI 서버 응답에 status가 없습니다.");
		}
		List<RecognizedPlace> places = new ArrayList<>();
		if ("CANDIDATES_FOUND".equals(payload.status()) && payload.candidates() != null) {
			for (CandidatePayload candidate : payload.candidates()) {
				RecognizedPlace place = toPlace(candidate);
				if (place != null) {
					places.add(place);
				}
				if (places.size() == MAX_CANDIDATES) {
					break;
				}
			}
		}
		return new Recognition(blankToNull(payload.extractedQuery()), places);
	}

	private RecognizedPlace toPlace(CandidatePayload c) {
		if (c == null || !KAKAO.equals(c.provider()) || isBlank(c.providerPlaceId()) || isBlank(c.name())) {
			return null;
		}
		if (c.latitude() == null || c.longitude() == null) {
			return null;
		}
		String road = blankToNull(c.roadAddress());
		String jibun = blankToNull(c.jibunAddress());
		String address = jibun != null ? jibun : road != null ? road : blankToNull(c.address());
		if (address == null) {
			return null;
		}
		PlaceCandidate candidate = new PlaceCandidate(c.providerPlaceId(), c.name().trim(), address, road,
				blankToNull(c.categoryLabel()), kakaoGroupCode(c.categoryCode()), c.latitude(), c.longitude(),
				blankToNull(c.sourceUrl()));
		return new RecognizedPlace(candidate, c.matchType(), c.matchReasons(), toTags(c));
	}

	private List<Tag> toTags(CandidatePayload c) {
		if (c.tags() == null || c.tagPostCount() == null || c.tagPostCount() < MIN_TAG_POST_COUNT) {
			return List.of();
		}
		return c.tags().stream()
				.filter(tag -> tag != null && !isBlank(tag.label()) && tag.mentionCount() != null)
				.limit(MAX_TAGS)
				.map(tag -> new Tag(tag.label(), tag.mentionCount()))
				.toList();
	}

	/** AI의 7개 카테고리 코드를 카카오 카테고리 그룹 코드로 되돌린다(대응이 없는 코드는 null). */
	static String kakaoGroupCode(String categoryCode) {
		if (categoryCode == null) {
			return null;
		}
		return switch (categoryCode) {
			case "MEAL" -> "FD6";
			case "CAFE" -> "CE7";
			case "CULTURE" -> "CT1";
			case "WALK" -> "AT4";
			default -> null;
		};
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static String blankToNull(String value) {
		return isBlank(value) ? null : value.trim();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RecognitionPayload(String status, String extractedQuery, List<CandidatePayload> candidates) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record CandidatePayload(String name, String address, Double latitude, Double longitude, String sourceUrl,
			String provider, String providerPlaceId, String roadAddress, String jibunAddress, String categoryLabel,
			String categoryCode, String matchType, List<String> matchReasons, List<TagPayload> tags,
			Integer tagPostCount) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TagPayload(String label, Integer mentionCount) {
	}
}
