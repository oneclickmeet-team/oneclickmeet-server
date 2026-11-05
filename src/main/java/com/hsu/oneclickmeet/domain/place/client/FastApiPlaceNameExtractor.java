package com.hsu.oneclickmeet.domain.place.client;

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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * AI 서버(FastAPI) 호출 클라이언트. 호출 계약은 아직 협의 중인 제안안이다.
 * <pre>
 * POST /v1/places/extract/text   {"text": "..."}          -> {"placeNames": ["..."]}
 * POST /v1/places/extract/image  multipart "image" 파일    -> {"placeNames": ["..."]}
 * </pre>
 * 연결 실패와 5xx에 한해 1회 재시도한다. 사용자가 보낸 텍스트·이미지와 추출 결과는 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class FastApiPlaceNameExtractor implements PlaceNameExtractor {

	static final int MAX_NAMES = 5;
	static final int MAX_NAME_LENGTH = 100;
	private static final int MAX_ATTEMPTS = 2;

	private final RestClient restClient;
	private final AiServerProperties properties;

	public FastApiPlaceNameExtractor(@Qualifier("aiServerRestClient") RestClient restClient,
			AiServerProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	@Override
	public List<String> fromText(String sharedText) {
		return call(() -> restClient.post()
				.uri("/v1/places/extract/text")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("text", sharedText))
				.retrieve()
				.body(ExtractResponse.class));
	}

	@Override
	public List<String> fromImage(byte[] image, String contentType) {
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
					.uri("/v1/places/extract/image")
					.contentType(MediaType.MULTIPART_FORM_DATA)
					.body(body)
					.retrieve()
					.body(ExtractResponse.class);
		});
	}

	private List<String> call(Supplier<ExtractResponse> request) {
		if (!properties.configured()) {
			throw new PlaceUpstreamException(Reason.AUTH, "AI 서버 주소가 설정되지 않았습니다.");
		}
		for (int attempt = 1; ; attempt++) {
			try {
				return toNames(request.get());
			} catch (HttpClientErrorException e) {
				int status = e.getStatusCode().value();
				Reason reason = status == 401 || status == 403 ? Reason.AUTH
						: status == 429 ? Reason.RATE_LIMIT : Reason.REQUEST_REJECTED;
				log.warn("AI 서버가 요청을 거부했습니다. status={}", status);
				throw new PlaceUpstreamException(reason, "AI 서버 요청 거부", e);
			} catch (HttpServerErrorException e) {
				if (attempt < MAX_ATTEMPTS) {
					log.warn("AI 서버 오류, 재시도합니다. status={}", e.getStatusCode().value());
					continue;
				}
				log.warn("AI 서버 오류. status={}", e.getStatusCode().value());
				throw new PlaceUpstreamException(Reason.SERVER_ERROR, "AI 서버 오류", e);
			} catch (ResourceAccessException e) {
				if (attempt < MAX_ATTEMPTS) {
					log.warn("AI 서버 연결 실패, 재시도합니다.");
					continue;
				}
				log.warn("AI 서버 연결 실패 또는 시간 초과");
				throw new PlaceUpstreamException(Reason.NETWORK, "AI 서버 연결 실패", e);
			} catch (RestClientException e) {
				log.warn("AI 서버 응답을 읽을 수 없습니다.");
				throw new PlaceUpstreamException(Reason.BAD_RESPONSE, "AI 서버 응답 오류", e);
			}
		}
	}

	private List<String> toNames(ExtractResponse response) {
		if (response == null || response.placeNames() == null) {
			throw new PlaceUpstreamException(Reason.BAD_RESPONSE, "AI 서버 응답에 placeNames가 없습니다.");
		}
		Set<String> names = new LinkedHashSet<>();
		for (String name : response.placeNames()) {
			if (name == null || name.isBlank()) {
				continue;
			}
			String trimmed = name.trim();
			if (trimmed.length() <= MAX_NAME_LENGTH) {
				names.add(trimmed);
			}
			if (names.size() == MAX_NAMES) {
				break;
			}
		}
		return List.copyOf(names);
	}

	record ExtractResponse(List<String> placeNames) {
	}
}
