package com.hsu.oneclickmeet.domain.recommendation.service;

import com.hsu.oneclickmeet.domain.place.entity.SavedPlace;
import com.hsu.oneclickmeet.domain.place.exception.PlaceErrorCode;
import com.hsu.oneclickmeet.domain.place.repository.SavedPlaceRepository;
import com.hsu.oneclickmeet.domain.recommendation.client.CourseRecommendationCommand;
import com.hsu.oneclickmeet.domain.recommendation.client.CourseRecommendationCommand.SavedPlaceInput;
import com.hsu.oneclickmeet.domain.recommendation.client.CourseRecommendations;
import com.hsu.oneclickmeet.domain.recommendation.client.CourseRecommender;
import com.hsu.oneclickmeet.domain.recommendation.dto.request.RecommendCoursesRequest;
import com.hsu.oneclickmeet.domain.recommendation.dto.request.RecommendCoursesRequest.Anchor;
import com.hsu.oneclickmeet.domain.recommendation.dto.request.RecommendCoursesRequest.AnchorType;
import com.hsu.oneclickmeet.domain.recommendation.dto.response.CourseRecommendationResponse;
import com.hsu.oneclickmeet.domain.recommendation.dto.response.CourseRecommendationResponse.RecommendedCourse;
import com.hsu.oneclickmeet.domain.recommendation.dto.response.CourseRecommendationResponse.RecommendedPlace;
import com.hsu.oneclickmeet.domain.recommendation.exception.RecommendationErrorCode;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 코스 추천. 요청(기준 지역 또는 보관함 장소, 방문 순서별 카테고리, 필수 장소 위치)과 내 보관함 장소를
 * AI 서버에 보내 코스를 계산하게 하고 결과를 앱 응답으로 바꾼다(TRD 4.2, 6.1).
 * 결과는 저장하지 않는다. AI 서버를 쓸 수 없으면 다른 방식으로 대신하지 않고 502로 응답한다.
 * 공동 장소 기준(SHARED)은 아직 지원하지 않는다.
 */
@Service
public class CourseRecommendationService {

	static final int SAVED_PLACE_LIMIT = 500;

	private final SavedPlaceRepository savedPlaceRepository;
	private final CourseRecommender recommender;
	private final TransactionTemplate transaction;

	public CourseRecommendationService(SavedPlaceRepository savedPlaceRepository, CourseRecommender recommender,
			PlatformTransactionManager transactionManager) {
		this.savedPlaceRepository = savedPlaceRepository;
		this.recommender = recommender;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	public CourseRecommendationResponse recommend(long memberId, RecommendCoursesRequest request) {
		Anchor anchor = request.anchor();
		SupportedArea area = null;
		if (anchor.type() == AnchorType.REGION) {
			area = SupportedArea.find(anchor.regionId())
					.orElseThrow(() -> new BusinessException(RecommendationErrorCode.UNSUPPORTED_AREA));
		}

		List<SavedPlace> saved = transaction.execute(status -> savedPlaceRepository
				.findTop500ByMemberIdOrderByIdDesc(memberId));
		Map<Long, SavedPlace> ownedById = new HashMap<>();
		saved.forEach(place -> ownedById.put(place.getId(), place));

		SavedPlace anchorPlace = null;
		if (anchor.type() == AnchorType.PLACE) {
			anchorPlace = transaction.execute(status -> savedPlaceRepository
					.findByIdAndMemberId(anchor.placeId(), memberId).orElse(null));
			if (anchorPlace == null) {
				throw new BusinessException(PlaceErrorCode.NOT_FOUND);
			}
			ownedById.put(anchorPlace.getId(), anchorPlace);
		}

		CourseRecommendationCommand command = commandOf(request, area, anchorPlace, saved);
		CourseRecommendations result = recommender.recommend(command);
		if (result.courses().isEmpty()) {
			return CourseRecommendationResponse.notEnough(result.reason());
		}
		return new CourseRecommendationResponse(CourseRecommendationResponse.Status.OK, null,
				result.courses().stream().map(course -> toResponse(course, ownedById)).toList());
	}

	private static CourseRecommendationCommand commandOf(RecommendCoursesRequest request, SupportedArea area,
			SavedPlace anchorPlace, List<SavedPlace> saved) {
		Long anchorId = anchorPlace == null ? null : anchorPlace.getId();
		List<SavedPlaceInput> savedInputs = saved.stream()
				.filter(place -> !place.getId().equals(anchorId))
				.map(CourseRecommendationService::inputOf)
				.toList();
		List<String> exclude = request.excludeCandidateKeys() == null ? List.of() : request.excludeCandidateKeys();
		return new CourseRecommendationCommand(
				area == null ? null : area.name(),
				anchorPlace == null ? null : inputOf(anchorPlace),
				request.placeCount(),
				request.categorySequence().stream().map(Enum::name).toList(),
				request.requiredPlaceOrder(),
				savedInputs,
				exclude);
	}

	private static SavedPlaceInput inputOf(SavedPlace place) {
		CourseCategory category = CourseCategory.fromKakaoGroupCode(place.getCategoryGroupCode());
		String address = place.getRoadAddress() != null ? place.getRoadAddress() : place.getAddress();
		return new SavedPlaceInput(place.getId(), place.getName(), address, place.getLat(), place.getLng(),
				category == null ? null : category.name());
	}

	/** 보관함 장소는 우리 DB 값(카카오 장소 ID, 주소, 링크)으로 채우고, 외부 후보는 AI 서버 값을 그대로 쓴다. */
	private static RecommendedCourse toResponse(CourseRecommendations.Course course, Map<Long, SavedPlace> ownedById) {
		List<RecommendedPlace> places = course.places().stream().map(place -> {
			SavedPlace owned = place.placeId() == null ? null : ownedById.get(place.placeId());
			if (owned == null) {
				return new RecommendedPlace(place.order(), place.source(), place.anchor(), place.providerPlaceId(),
						place.name(), place.address(), place.roadAddress(), place.categoryLabel(),
						place.categoryCode(), place.lat(), place.lng(), place.placeUrl(), null,
						place.walkMinutesFromPrevious());
			}
			return new RecommendedPlace(place.order(), place.source(), place.anchor(), owned.getProviderPlaceId(),
					owned.getName(), owned.getAddress(), owned.getRoadAddress(), place.categoryLabel(),
					place.categoryCode(), owned.getLat(), owned.getLng(), owned.getPlaceUrl(), owned.getId(),
					place.walkMinutesFromPrevious());
		}).toList();
		return new RecommendedCourse(course.candidateKey(), course.title(), course.totalWalkMinutes(), places);
	}
}
