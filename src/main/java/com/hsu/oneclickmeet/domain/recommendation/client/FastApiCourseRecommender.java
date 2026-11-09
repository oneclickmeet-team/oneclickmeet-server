package com.hsu.oneclickmeet.domain.recommendation.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hsu.oneclickmeet.domain.place.client.AiServerProperties;
import com.hsu.oneclickmeet.domain.recommendation.client.CourseRecommendationCommand.SavedPlaceInput;
import com.hsu.oneclickmeet.domain.recommendation.client.CourseRecommendations.Course;
import com.hsu.oneclickmeet.domain.recommendation.client.CourseRecommendations.Place;
import com.hsu.oneclickmeet.domain.recommendation.exception.RecommendationUpstreamException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 서버(FastAPI) 코스 추천 호출(R01). 계약은 AI 파트의 API_CONVENTION(v2.1) 3.5절을 따른다.
 * <pre>POST /internal/v1/course-recommendations -> courses[], reason</pre>
 * 재시도하지 않으며 어떤 실패든 RecommendationUpstreamException으로 바꾼다. 실패했을 때 다른 방식으로
 * 추천을 대신하지 않는다(TRD: 외부 장애를 유효한 추천처럼 반환하지 않는다).
 * 요청에 담긴 보관함 장소 정보와 추천 결과는 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class FastApiCourseRecommender implements CourseRecommender {

	private static final String KAKAO = "KAKAO";

	private final RestClient restClient;
	private final AiServerProperties properties;

	public FastApiCourseRecommender(@Qualifier("aiRecommendRestClient") RestClient restClient,
			AiServerProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	@Override
	public CourseRecommendations recommend(CourseRecommendationCommand command) {
		if (!properties.configured()) {
			log.error("AI 서버 주소가 설정되지 않아 코스를 추천할 수 없습니다.");
			throw new RecommendationUpstreamException(null);
		}
		ResponsePayload payload;
		try {
			payload = restClient.post()
					.uri("/internal/v1/course-recommendations")
					.contentType(MediaType.APPLICATION_JSON)
					.body(requestBody(command))
					.retrieve()
					.body(ResponsePayload.class);
		} catch (HttpClientErrorException e) {
			// 400·401·404는 우리 쪽 요청·설정 오류라 서버 로그에서 바로 보이게 error로 남긴다.
			log.error("AI 서버가 코스 추천 요청을 거부했습니다. status={}", e.getStatusCode().value());
			throw new RecommendationUpstreamException(e);
		} catch (HttpServerErrorException e) {
			log.warn("AI 서버 코스 추천 오류. status={}", e.getStatusCode().value());
			throw new RecommendationUpstreamException(e);
		} catch (ResourceAccessException e) {
			log.warn("AI 서버 연결 실패 또는 시간 초과(코스 추천)");
			throw new RecommendationUpstreamException(e);
		} catch (RestClientException e) {
			log.warn("AI 서버 코스 추천 응답을 읽을 수 없습니다.");
			throw new RecommendationUpstreamException(e);
		}
		return toRecommendations(payload);
	}

	private static Map<String, Object> requestBody(CourseRecommendationCommand command) {
		Map<String, Object> body = new LinkedHashMap<>();
		if (command.anchorPlace() != null) {
			body.put("anchor", Map.of("type", "PLACE", "placeId", command.anchorPlace().placeId()));
			body.put("anchorPlace", savedPlace(command.anchorPlace()));
		} else {
			body.put("anchor", Map.of("type", "REGION", "regionId", command.regionId()));
		}
		body.put("placeCount", command.placeCount());
		body.put("categorySequence", command.categorySequence());
		if (command.requiredPlaceOrder() != null) {
			body.put("requiredPlaceOrder", command.requiredPlaceOrder());
		}
		body.put("savedPlaces", command.savedPlaces().stream().map(FastApiCourseRecommender::savedPlace).toList());
		body.put("excludeCandidateKeys", command.excludeCandidateKeys());
		return body;
	}

	private static Map<String, Object> savedPlace(SavedPlaceInput place) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("placeId", place.placeId());
		map.put("source", "PERSONAL");
		map.put("name", place.name());
		map.put("address", place.address());
		map.put("latitude", place.latitude());
		map.put("longitude", place.longitude());
		map.put("categoryCode", place.categoryCode());
		return map;
	}

	private static CourseRecommendations toRecommendations(ResponsePayload payload) {
		if (payload == null || payload.courses() == null) {
			log.warn("AI 서버 코스 추천 응답에 courses가 없습니다.");
			throw new RecommendationUpstreamException(null);
		}
		List<Course> courses = new ArrayList<>();
		for (CoursePayload course : payload.courses()) {
			courses.add(toCourse(course));
		}
		return new CourseRecommendations(courses, blankToNull(payload.reason()));
	}

	private static Course toCourse(CoursePayload course) {
		if (course == null || blank(course.candidateKey()) || course.places() == null || course.places().isEmpty()) {
			log.warn("AI 서버 코스 추천 응답의 코스 형식이 올바르지 않습니다.");
			throw new RecommendationUpstreamException(null);
		}
		Map<Integer, Integer> minutesTo = new HashMap<>();
		if (course.walkingLegs() != null) {
			for (LegPayload leg : course.walkingLegs()) {
				if (leg != null && leg.toVisitOrder() != null && leg.estimatedMinutes() != null) {
					minutesTo.put(leg.toVisitOrder(), leg.estimatedMinutes());
				}
			}
		}
		List<Place> places = new ArrayList<>();
		for (PlacePayload place : course.places()) {
			places.add(toPlace(place, minutesTo));
		}
		int total = course.totalWalkingMinutes() == null ? 0 : course.totalWalkingMinutes();
		return new Course(course.candidateKey(), blankToNull(course.title()), total, places);
	}

	private static Place toPlace(PlacePayload place, Map<Integer, Integer> minutesTo) {
		if (place == null || place.visitOrder() == null || blank(place.name()) || place.latitude() == null
				|| place.longitude() == null) {
			log.warn("AI 서버 코스 추천 응답의 장소 형식이 올바르지 않습니다.");
			throw new RecommendationUpstreamException(null);
		}
		String road = blankToNull(place.roadAddress());
		String jibun = blankToNull(place.jibunAddress());
		String address = jibun != null ? jibun : road != null ? road : blankToNull(place.address());
		String providerPlaceId = KAKAO.equals(place.provider()) ? blankToNull(place.providerPlaceId()) : null;
		return new Place(place.visitOrder(), place.source(), Boolean.TRUE.equals(place.isAnchor()), place.placeId(),
				providerPlaceId, place.name().trim(), address, road, place.latitude(), place.longitude(),
				blankToNull(place.categoryCode()), blankToNull(place.categoryLabel()), blankToNull(place.sourceUrl()),
				minutesTo.get(place.visitOrder()));
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private static String blankToNull(String value) {
		return blank(value) ? null : value.trim();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ResponsePayload(List<CoursePayload> courses, String reason) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record CoursePayload(String candidateKey, String title, List<PlacePayload> places, List<LegPayload> walkingLegs,
			Integer totalWalkingMinutes) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record PlacePayload(Integer visitOrder, String name, String address, String roadAddress, String jibunAddress,
			Double latitude, Double longitude, String categoryCode, String categoryLabel, String source, Long placeId,
			Boolean isAnchor, String sourceUrl, String provider, String providerPlaceId) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record LegPayload(Integer fromVisitOrder, Integer toVisitOrder, Integer estimatedMinutes) {
	}
}
