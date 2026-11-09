package com.hsu.oneclickmeet.domain.place.client;

import com.hsu.oneclickmeet.domain.place.client.kakao.KakaoKeywordSearchResponse;
import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException;
import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException.Reason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 카카오 로컬 API 키워드 검색 클라이언트.
 * 연결 실패와 5xx에 한해 1회 재시도하고, 4xx(한도 초과 포함)는 재시도하지 않는다.
 * 검색어는 사용자 입력이므로 로그에 남기지 않는다.
 * 실제로 보낸 요청마다 kakao.local.requests 카운터를 결과(outcome) 태그와 함께 1 올린다(한도 관찰용).
 */
@Slf4j
@Component
public class KakaoPlaceSearchClient implements PlaceSearchClient {

	private static final String SEARCH_PATH = "/v2/local/search/keyword.json";
	private static final int MAX_ATTEMPTS = 2;

	static final String REQUEST_METRIC = "kakao.local.requests";

	private final RestClient restClient;
	private final KakaoLocalProperties properties;
	private final MeterRegistry meterRegistry;

	public KakaoPlaceSearchClient(@Qualifier("kakaoLocalRestClient") RestClient restClient,
								  KakaoLocalProperties properties, MeterRegistry meterRegistry) {
		this.restClient = restClient;
		this.properties = properties;
		this.meterRegistry = meterRegistry;
	}

	private void record(String outcome) {
		Counter.builder(REQUEST_METRIC)
				.description("카카오 로컬 API로 보낸 요청 수")
				.tag("outcome", outcome)
				.register(meterRegistry)
				.increment();
	}

	@Override
	public PlaceSearchResult search(PlaceSearchCommand command) {
		if (properties.restApiKey() == null || properties.restApiKey().isBlank()) {
			log.error("카카오 로컬 API 키가 설정되지 않았습니다.");
			throw new PlaceUpstreamException(Reason.AUTH, "카카오 로컬 API 키가 설정되지 않았습니다.");
		}
		return toResult(command, requestWithRetry(command));
	}

	private KakaoKeywordSearchResponse requestWithRetry(PlaceSearchCommand command) {
		for (int attempt = 1; ; attempt++) {
			try {
				KakaoKeywordSearchResponse response = request(command);
				record("success");
				return response;
			} catch (HttpClientErrorException e) {
				PlaceUpstreamException rejected = rejected(e);
				record(rejected.getReason().name().toLowerCase());
				throw rejected;
			} catch (HttpServerErrorException e) {
				record("server_error");
				if (attempt < MAX_ATTEMPTS) {
					log.warn("카카오 로컬 API 서버 오류, 재시도합니다. status={}", e.getStatusCode().value());
					continue;
				}
				log.warn("카카오 로컬 API 서버 오류. status={}", e.getStatusCode().value());
				throw new PlaceUpstreamException(Reason.SERVER_ERROR, "카카오 로컬 API 서버 오류", e);
			} catch (ResourceAccessException e) {
				record("network");
				if (attempt < MAX_ATTEMPTS) {
					log.warn("카카오 로컬 API 연결 실패, 재시도합니다.");
					continue;
				}
				log.warn("카카오 로컬 API 연결 실패 또는 시간 초과");
				throw new PlaceUpstreamException(Reason.NETWORK, "카카오 로컬 API 연결 실패", e);
			} catch (RestClientException e) {
				record("bad_response");
				log.warn("카카오 로컬 API 응답을 읽을 수 없습니다.");
				throw new PlaceUpstreamException(Reason.BAD_RESPONSE, "카카오 로컬 API 응답 오류", e);
			}
		}
	}

	private KakaoKeywordSearchResponse request(PlaceSearchCommand command) {
		Map<String, Object> variables = new HashMap<>();
		variables.put("query", command.query());
		variables.put("page", command.page());
		variables.put("size", command.size());
		KakaoKeywordSearchResponse body = restClient.get()
				.uri(uriBuilder -> {
					uriBuilder.path(SEARCH_PATH)
							.queryParam("query", "{query}")
							.queryParam("page", "{page}")
							.queryParam("size", "{size}");
					if (command.hasLocation()) {
						uriBuilder.queryParam("x", "{x}").queryParam("y", "{y}");
						variables.put("x", command.lng());
						variables.put("y", command.lat());
					}
					return uriBuilder.build(variables);
				})
				.header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.restApiKey())
				.retrieve()
				.body(KakaoKeywordSearchResponse.class);
		if (body == null) {
			record("bad_response");
			throw new PlaceUpstreamException(Reason.BAD_RESPONSE, "카카오 로컬 API 응답이 비어 있습니다.");
		}
		return body;
	}

	private PlaceUpstreamException rejected(HttpClientErrorException e) {
		int status = e.getStatusCode().value();
		if (status == 401 || status == 403) {
			log.error("카카오 로컬 API 인증 실패. API 키와 앱 설정을 확인해야 합니다. status={}", status);
			return new PlaceUpstreamException(Reason.AUTH, "카카오 로컬 API 인증 실패", e);
		}
		if (status == 429) {
			log.warn("카카오 로컬 API 호출 한도를 초과했습니다.");
			return new PlaceUpstreamException(Reason.RATE_LIMIT, "카카오 로컬 API 호출 한도 초과", e);
		}
		log.error("카카오 로컬 API가 요청을 거부했습니다. 요청 파라미터를 확인해야 합니다. status={}", status);
		return new PlaceUpstreamException(Reason.REQUEST_REJECTED, "카카오 로컬 API 요청 거부", e);
	}

	private PlaceSearchResult toResult(PlaceSearchCommand command, KakaoKeywordSearchResponse body) {
		if (body.documents() == null || body.meta() == null || body.meta().isEnd() == null) {
			log.warn("카카오 로컬 API 응답에 필수 필드(documents, meta.is_end)가 없습니다.");
			throw new PlaceUpstreamException(Reason.BAD_RESPONSE, "카카오 로컬 API 응답에 필수 필드가 없습니다.");
		}
		List<PlaceCandidate> candidates = body.documents().stream().map(this::toCandidate).toList();
		boolean hasNext = !body.meta().isEnd();
		return new PlaceSearchResult(candidates, command.page(), hasNext);
	}

	private PlaceCandidate toCandidate(KakaoKeywordSearchResponse.Document document) {
		try {
			return new PlaceCandidate(
					document.id(),
					document.placeName(),
					document.addressName(),
					blankToNull(document.roadAddressName()),
					blankToNull(document.categoryName()),
					blankToNull(document.categoryGroupCode()),
					Double.parseDouble(document.y()),
					Double.parseDouble(document.x()),
					blankToNull(document.placeUrl()));
		} catch (NumberFormatException | NullPointerException e) {
			log.warn("카카오 로컬 API 응답의 좌표를 읽을 수 없습니다.");
			throw new PlaceUpstreamException(Reason.BAD_RESPONSE, "카카오 로컬 API 응답의 좌표 오류", e);
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
