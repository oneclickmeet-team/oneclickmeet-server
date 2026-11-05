package com.hsu.oneclickmeet.domain.recommendation.service;

import com.hsu.oneclickmeet.domain.place.client.PlaceCandidate;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchClient;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchCommand;
import com.hsu.oneclickmeet.domain.place.entity.SavedPlace;
import com.hsu.oneclickmeet.domain.place.exception.PlaceErrorCode;
import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException;
import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException.Reason;
import com.hsu.oneclickmeet.domain.place.repository.SavedPlaceRepository;
import com.hsu.oneclickmeet.domain.recommendation.dto.request.RecommendCoursesRequest;
import com.hsu.oneclickmeet.domain.recommendation.dto.response.CourseRecommendationResponse;
import com.hsu.oneclickmeet.domain.recommendation.dto.response.CourseRecommendationResponse.RecommendedCourse;
import com.hsu.oneclickmeet.domain.recommendation.dto.response.CourseRecommendationResponse.RecommendedPlace;
import com.hsu.oneclickmeet.domain.recommendation.exception.RecommendationErrorCode;
import com.hsu.oneclickmeet.domain.recommendation.service.CourseBuilder.Course;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * 코스 추천. 기준 장소(보관함) 또는 지원 지역의 중심에서 반경 1km 안의 후보(내 보관함 우선 + 카카오 검색)로
 * 장소 3~5곣, 장소 사이 도보 10분 이내인 코스를 최대 3개 만든다.
 * 결과는 저장하지 않는다. 카카오 검색이 실패하면 502, 후보가 부족하면 NOT_ENOUGH_CANDIDATES다.
 * (분위기 정보로 거르는 단계는 외부 요약 연동 정책이 정해진 뒤 추가한다.)
 */
@Service
public class CourseRecommendationService {

	static final List<String> SEARCH_KEYWORDS = List.of("카페", "맛집", "전시");
	static final int SEARCH_SIZE = 15;

	private final SavedPlaceRepository savedPlaceRepository;
	private final PlaceSearchClient searchClient;
	private final TransactionTemplate transaction;

	public CourseRecommendationService(SavedPlaceRepository savedPlaceRepository, PlaceSearchClient searchClient,
			PlatformTransactionManager transactionManager) {
		this.savedPlaceRepository = savedPlaceRepository;
		this.searchClient = searchClient;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	public CourseRecommendationResponse recommend(long memberId, RecommendCoursesRequest request) {
		boolean hasAnchor = request.anchorSavedPlaceId() != null;
		boolean hasArea = request.area() != null && !request.area().isBlank();
		if (hasAnchor == hasArea) {
			throw new BusinessException(RecommendationErrorCode.START_REQUIRED);
		}
		SupportedArea area = hasArea
				? SupportedArea.find(request.area()).orElseThrow(
						() -> new BusinessException(RecommendationErrorCode.UNSUPPORTED_AREA))
				: null;

		List<SavedPlace> saved = transaction.execute(status -> savedPlaceRepository
				.findTop200ByMemberIdOrderByIdDesc(memberId));
		CourseSpot anchor = null;
		double centerLat;
		double centerLng;
		if (hasAnchor) {
			SavedPlace anchorPlace = transaction.execute(status -> savedPlaceRepository
					.findByIdAndMemberId(request.anchorSavedPlaceId(), memberId).orElse(null));
			if (anchorPlace == null) {
				throw new BusinessException(PlaceErrorCode.NOT_FOUND);
			}
			anchor = spotOf(anchorPlace);
			centerLat = anchor.lat();
			centerLng = anchor.lng();
		} else {
			PlaceCandidate center = searchClient
					.search(new PlaceSearchCommand(area.searchKeyword(), 1, 1, null, null)).candidates().stream()
					.findFirst()
					.orElseThrow(() -> new PlaceUpstreamException(Reason.BAD_RESPONSE, "지역 중심을 찾지 못했습니다."));
			centerLat = center.lat();
			centerLng = center.lng();
		}

		List<CourseSpot> candidates = new ArrayList<>();
		saved.forEach(place -> candidates.add(spotOf(place)));
		for (String keyword : SEARCH_KEYWORDS) {
			for (PlaceCandidate candidate : searchClient
					.search(new PlaceSearchCommand(keyword, 1, SEARCH_SIZE, centerLat, centerLng)).candidates()) {
				candidates.add(spotOf(candidate));
			}
		}

		List<Course> courses = CourseBuilder.build(centerLat, centerLng, anchor, candidates);
		if (courses.isEmpty()) {
			return CourseRecommendationResponse.notEnough();
		}
		return new CourseRecommendationResponse(CourseRecommendationResponse.Status.OK,
				courses.stream().map(CourseRecommendationService::toResponse).toList());
	}

	private static CourseSpot spotOf(SavedPlace place) {
		return new CourseSpot(place.getProviderPlaceId(), place.getName(), place.getAddress(), place.getRoadAddress(),
				place.getCategory(), place.getCategoryGroupCode(), place.getLat(), place.getLng(),
				place.getPlaceUrl(), place.getId());
	}

	private static CourseSpot spotOf(PlaceCandidate candidate) {
		return new CourseSpot(candidate.providerPlaceId(), candidate.name(), candidate.address(),
				candidate.roadAddress(), candidate.category(), candidate.categoryGroupCode(), candidate.lat(),
				candidate.lng(), candidate.placeUrl(), null);
	}

	private static RecommendedCourse toResponse(Course course) {
		List<RecommendedPlace> places = new ArrayList<>();
		for (int i = 0; i < course.spots().size(); i++) {
			CourseSpot spot = course.spots().get(i);
			places.add(new RecommendedPlace(i + 1, spot.providerPlaceId(), spot.name(), spot.address(),
					spot.roadAddress(), spot.category(), spot.categoryGroupCode(), spot.lat(), spot.lng(),
					spot.placeUrl(), spot.savedPlaceId(), i == 0 ? null : course.legMinutes().get(i - 1)));
		}
		return new RecommendedCourse(candidateKey(course), course.totalMinutes(), places);
	}

	/** 같은 장소 구성이면 같은 키가 되는 안정적인 식별값. 저장 연결 방식은 course 담당과 합의 후 정한다. */
	private static String candidateKey(Course course) {
		String joined = String.join("|", course.spots().stream().map(CourseSpot::providerPlaceId).toList());
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(digest, 12));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
