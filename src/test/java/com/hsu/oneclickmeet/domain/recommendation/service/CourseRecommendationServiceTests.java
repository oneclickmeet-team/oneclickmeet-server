package com.hsu.oneclickmeet.domain.recommendation.service;

import com.hsu.oneclickmeet.domain.place.entity.PlaceProvider;
import com.hsu.oneclickmeet.domain.place.entity.SavedPlace;
import com.hsu.oneclickmeet.domain.place.exception.PlaceErrorCode;
import com.hsu.oneclickmeet.domain.place.repository.SavedPlaceRepository;
import com.hsu.oneclickmeet.domain.recommendation.client.CourseRecommendationCommand;
import com.hsu.oneclickmeet.domain.recommendation.client.CourseRecommendations;
import com.hsu.oneclickmeet.domain.recommendation.client.CourseRecommendations.Course;
import com.hsu.oneclickmeet.domain.recommendation.client.CourseRecommendations.Place;
import com.hsu.oneclickmeet.domain.recommendation.client.CourseRecommender;
import com.hsu.oneclickmeet.domain.recommendation.dto.request.RecommendCoursesRequest;
import com.hsu.oneclickmeet.domain.recommendation.dto.request.RecommendCoursesRequest.Anchor;
import com.hsu.oneclickmeet.domain.recommendation.dto.request.RecommendCoursesRequest.AnchorType;
import com.hsu.oneclickmeet.domain.recommendation.dto.response.CourseRecommendationResponse;
import com.hsu.oneclickmeet.domain.recommendation.dto.response.CourseRecommendationResponse.RecommendedPlace;
import com.hsu.oneclickmeet.domain.recommendation.dto.response.CourseRecommendationResponse.Status;
import com.hsu.oneclickmeet.domain.recommendation.exception.RecommendationErrorCode;
import com.hsu.oneclickmeet.domain.recommendation.exception.RecommendationUpstreamException;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseRecommendationServiceTests {

	private static final long MEMBER = 7L;
	private static final List<CourseCategory> SEQUENCE = List.of(CourseCategory.MEAL, CourseCategory.CAFE,
		CourseCategory.WALK);

	private final SavedPlaceRepository savedRepository = mock(SavedPlaceRepository.class);
	private final FakeRecommender recommender = new FakeRecommender();
	private final CourseRecommendationService service = new CourseRecommendationService(savedRepository, recommender,
		mock(PlatformTransactionManager.class));

	private static SavedPlace saved(long id, String group, String road) {
		SavedPlace place = SavedPlace.create(MEMBER, PlaceProvider.KAKAO, "kakao-" + id, "내 장소 " + id, "지번 주소 " + id,
			road, "음식점", group, 37.5, 127.0, "http://place.map.kakao.com/" + id);
		ReflectionTestUtils.setField(place, "id", id);
		return place;
	}

	private static RecommendCoursesRequest region(String regionId) {
		return new RecommendCoursesRequest(new Anchor(AnchorType.REGION, regionId, null), 3, SEQUENCE, null, null);
	}

	private static RecommendCoursesRequest place(long placeId, int requiredOrder, List<String> exclude) {
		return new RecommendCoursesRequest(new Anchor(AnchorType.PLACE, null, placeId), 3, SEQUENCE, requiredOrder,
			exclude);
	}

	private static String code(Throwable thrown) {
		assertThat(thrown).isInstanceOf(BusinessException.class);
		return ((BusinessException) thrown).getErrorCode().getCode();
	}

	private static Place place(int order, String source, Long placeId, String providerPlaceId, String name) {
		return new Place(order, source, false, placeId, providerPlaceId, name, "AI 주소", "AI 도로명", 37.1, 127.1, "CAFE",
			"카페", "http://ai.example/" + order, order == 1 ? null : 5);
	}

	@Test
	@DisplayName("지역 기준이면 지역 ID·카테고리 순서·보관함 장소를 AI 서버에 보내고 필수 장소 위치는 보내지 않는다")
	void sendsRegionRequest() {
		when(savedRepository.findTop500ByMemberIdOrderByIdDesc(MEMBER))
			.thenReturn(List.of(saved(11, "CE7", "도로명 11"), saved(12, "FD6", null), saved(13, "SW8", null)));
		recommender.result = new CourseRecommendations(List.of(), "INSUFFICIENT_PLACES");

		service.recommend(MEMBER, region("SUNGSHIN"));

		CourseRecommendationCommand command = recommender.last;
		assertThat(command.regionId()).isEqualTo("SUNGSHIN");
		assertThat(command.anchorPlace()).isNull();
		assertThat(command.placeCount()).isEqualTo(3);
		assertThat(command.categorySequence()).containsExactly("MEAL", "CAFE", "WALK");
		assertThat(command.requiredPlaceOrder()).isNull();
		assertThat(command.excludeCandidateKeys()).isEmpty();
		assertThat(command.savedPlaces()).extracting("placeId").containsExactly(11L, 12L, 13L);
		assertThat(command.savedPlaces()).extracting("categoryCode").containsExactly("CAFE", "MEAL", null);
		assertThat(command.savedPlaces().get(0).address()).isEqualTo("도로명 11");
		assertThat(command.savedPlaces().get(1).address()).isEqualTo("지번 주소 12");
	}

	@Test
	@DisplayName("화면 이름으로 지역을 보내도 AI 서버에는 지역 ID로 보낸다")
	void convertsDisplayNameToRegionId() {
		recommender.result = new CourseRecommendations(List.of(), "INSUFFICIENT_PLACES");

		service.recommend(MEMBER, region("한성대입구역"));

		assertThat(recommender.last.regionId()).isEqualTo("HANSUNG");
	}

	@Test
	@DisplayName("장소 기준이면 기준 장소·필수 위치·제외 키를 보내고 기준 장소는 보관함 목록에서 뺀다")
	void sendsPlaceAnchorRequest() {
		SavedPlace anchor = saved(11, "CE7", null);
		when(savedRepository.findTop500ByMemberIdOrderByIdDesc(MEMBER)).thenReturn(List.of(anchor, saved(12, "FD6", null)));
		when(savedRepository.findByIdAndMemberId(11L, MEMBER)).thenReturn(Optional.of(anchor));
		recommender.result = new CourseRecommendations(List.of(), "INSUFFICIENT_PLACES");

		service.recommend(MEMBER, place(11, 2, List.of("crs_a", "crs_b")));

		CourseRecommendationCommand command = recommender.last;
		assertThat(command.regionId()).isNull();
		assertThat(command.anchorPlace().placeId()).isEqualTo(11L);
		assertThat(command.requiredPlaceOrder()).isEqualTo(2);
		assertThat(command.excludeCandidateKeys()).containsExactly("crs_a", "crs_b");
		assertThat(command.savedPlaces()).extracting("placeId").containsExactly(12L);
	}

	@Test
	@DisplayName("지원하지 않는 지역이면 400이고 AI 서버를 부르지 않는다")
	void rejectsUnsupportedRegion() {
		assertThatThrownBy(() -> service.recommend(MEMBER, region("강남")))
			.satisfies(thrown -> assertThat(code(thrown)).isEqualTo("RECOMMENDATION-0401"));
		assertThat(recommender.last).isNull();
	}

	@Test
	@DisplayName("내 보관함에 없는 기준 장소(남의 장소 포함)는 404이고 AI 서버를 부르지 않는다")
	void rejectsUnknownAnchor() {
		when(savedRepository.findByIdAndMemberId(anyLong(), anyLong())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.recommend(MEMBER, place(99, 1, null)))
			.isInstanceOfSatisfying(BusinessException.class,
				thrown -> assertThat(thrown.getErrorCode()).isEqualTo(PlaceErrorCode.NOT_FOUND));
		assertThat(recommender.last).isNull();
	}

	@Test
	@DisplayName("보관함 장소는 우리 DB 값으로, 외부 후보는 AI 서버 값으로 채워 돌려준다")
	void mapsResult() {
		SavedPlace owned = saved(11, "CE7", "도로명 11");
		when(savedRepository.findTop500ByMemberIdOrderByIdDesc(MEMBER)).thenReturn(List.of(owned));
		recommender.result = new CourseRecommendations(List.of(new Course("crs_x", "성신 코스", 9, List.of(
			place(1, "PERSONAL", 11L, null, "AI가 부른 이름"),
			place(2, "EXTERNAL", null, "ext-1", "외부 카페")))), null);

		CourseRecommendationResponse response = service.recommend(MEMBER, region("JAMSIL"));

		assertThat(response.status()).isEqualTo(Status.OK);
		assertThat(response.reason()).isNull();
		var course = response.courses().get(0);
		assertThat(course.candidateKey()).isEqualTo("crs_x");
		assertThat(course.title()).isEqualTo("성신 코스");
		assertThat(course.totalWalkMinutes()).isEqualTo(9);
		RecommendedPlace mine = course.places().get(0);
		assertThat(mine.savedPlaceId()).isEqualTo(11L);
		assertThat(mine.providerPlaceId()).isEqualTo("kakao-11");
		assertThat(mine.name()).isEqualTo("내 장소 11");
		assertThat(mine.address()).isEqualTo("지번 주소 11");
		assertThat(mine.roadAddress()).isEqualTo("도로명 11");
		assertThat(mine.placeUrl()).isEqualTo("http://place.map.kakao.com/11");
		assertThat(mine.walkMinutesFromPrevious()).isNull();
		RecommendedPlace external = course.places().get(1);
		assertThat(external.savedPlaceId()).isNull();
		assertThat(external.providerPlaceId()).isEqualTo("ext-1");
		assertThat(external.name()).isEqualTo("외부 카페");
		assertThat(external.address()).isEqualTo("AI 주소");
		assertThat(external.category()).isEqualTo("카페");
		assertThat(external.categoryCode()).isEqualTo("CAFE");
		assertThat(external.walkMinutesFromPrevious()).isEqualTo(5);
	}

	@Test
	@DisplayName("코스가 없으면 NOT_ENOUGH_CANDIDATES와 AI 서버가 준 이유를 돌려준다")
	void returnsNotEnoughWithReason() {
		recommender.result = new CourseRecommendations(List.of(), "NO_MORE_COURSES");

		CourseRecommendationResponse response = service.recommend(MEMBER, region("HYEHWA"));

		assertThat(response.status()).isEqualTo(Status.NOT_ENOUGH_CANDIDATES);
		assertThat(response.reason()).isEqualTo("NO_MORE_COURSES");
		assertThat(response.courses()).isEmpty();
	}

	@Test
	@DisplayName("AI 서버를 쓸 수 없으면 다른 방식으로 대신하지 않고 502를 그대로 올린다")
	void doesNotFallBackWhenAiServerFails() {
		recommender.failure = new RecommendationUpstreamException(null);

		assertThatThrownBy(() -> service.recommend(MEMBER, region("SEOCHON")))
			.isInstanceOfSatisfying(BusinessException.class,
				thrown -> assertThat(thrown.getErrorCode()).isEqualTo(RecommendationErrorCode.UPSTREAM_FAILURE));
	}

	@Test
	@DisplayName("카카오 분류 코드는 AI 카테고리로 바꾸고 대응이 없으면 null이다")
	void mapsKakaoGroupCodes() {
		assertThat(CourseCategory.fromKakaoGroupCode("FD6")).isEqualTo(CourseCategory.MEAL);
		assertThat(CourseCategory.fromKakaoGroupCode("CE7")).isEqualTo(CourseCategory.CAFE);
		assertThat(CourseCategory.fromKakaoGroupCode("CT1")).isEqualTo(CourseCategory.CULTURE);
		assertThat(CourseCategory.fromKakaoGroupCode("AT4")).isEqualTo(CourseCategory.WALK);
		assertThat(CourseCategory.fromKakaoGroupCode("SW8")).isNull();
		assertThat(CourseCategory.fromKakaoGroupCode(null)).isNull();
	}

	private static class FakeRecommender implements CourseRecommender {

		CourseRecommendationCommand last;
		CourseRecommendations result = new CourseRecommendations(List.of(), null);
		RuntimeException failure;

		@Override
		public CourseRecommendations recommend(CourseRecommendationCommand command) {
			last = command;
			if (failure != null) {
				throw failure;
			}
			return result;
		}
	}
}
