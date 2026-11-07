package com.hsu.oneclickmeet.domain.recommendation.service;

import com.hsu.oneclickmeet.domain.place.client.PlaceCandidate;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchClient;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchCommand;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchResult;
import com.hsu.oneclickmeet.domain.place.entity.PlaceProvider;
import com.hsu.oneclickmeet.domain.place.entity.SavedPlace;
import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException;
import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException.Reason;
import com.hsu.oneclickmeet.domain.place.repository.SavedPlaceRepository;
import com.hsu.oneclickmeet.domain.recommendation.dto.request.RecommendCoursesRequest;
import com.hsu.oneclickmeet.domain.recommendation.dto.response.CourseRecommendationResponse;
import com.hsu.oneclickmeet.domain.recommendation.dto.response.CourseRecommendationResponse.Status;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseRecommendationServiceTests {

	private static final long MEMBER = 7L;
	private static final double LAT = 37.5927;
	private static final double LNG = 127.0165;

	private final SavedPlaceRepository savedRepository = mock(SavedPlaceRepository.class);
	private final FakeSearch search = new FakeSearch();
	private final CourseRecommendationService service = new CourseRecommendationService(savedRepository, search,
		mock(PlatformTransactionManager.class));

	private static PlaceCandidate candidate(String id, String group, double dLat, double dLng) {
		return new PlaceCandidate(id, "장소 " + id, "주소", null, "음식점", group, LAT + dLat, LNG + dLng, null);
	}

	private static String code(Throwable thrown) {
		assertThat(thrown).isInstanceOf(BusinessException.class);
		return ((BusinessException) thrown).getErrorCode().getCode();
	}

	private void givenAreaCenterAndCandidates() {
		search.results.put("성신여대입구역", List.of(candidate("center", "SW8", 0, 0)));
		search.results.put("카페", List.of(candidate("c1", "CE7", 0.001, 0), candidate("c2", "CE7", 0.003, 0.001)));
		search.results.put("맛집", List.of(candidate("f1", "FD6", 0.002, 0), candidate("f2", "FD6", 0.004, 0)));
		search.results.put("전시", List.of(candidate("a1", "CT1", 0.0, 0.002)));
	}

	@Test
	@DisplayName("지역으로 추천하면 지역 중심을 찾고 그 위치를 기준으로 카페·맛집·전시를 검색해 코스를 만든다")
	void recommendsByArea() {
		givenAreaCenterAndCandidates();
		when(savedRepository.findTop200ByMemberIdOrderByIdDesc(MEMBER)).thenReturn(List.of());

		CourseRecommendationResponse response = service.recommend(MEMBER, new RecommendCoursesRequest(null, "성신여대입구역"));

		assertThat(response.status()).isEqualTo(Status.OK);
		assertThat(response.courses()).isNotEmpty().hasSizeLessThanOrEqualTo(3);
		assertThat(response.courses()).allSatisfy(course -> {
			assertThat(course.candidateKey()).isNotBlank();
			assertThat(course.places()).hasSizeBetween(3, 5);
			assertThat(course.places().get(0).walkMinutesFromPrevious()).isNull();
			assertThat(course.places().subList(1, course.places().size()))
				.allSatisfy(place -> assertThat(place.walkMinutesFromPrevious()).isBetween(0, 10));
		});
		assertThat(search.commands.get(0).query()).isEqualTo("성신여대입구역");
		assertThat(search.commands.get(0).hasLocation()).isFalse();
		assertThat(search.commands.subList(1, search.commands.size())).extracting(PlaceSearchCommand::query)
			.containsExactly("카페", "맛집", "전시");
		assertThat(search.commands.subList(1, search.commands.size())).allSatisfy(command -> {
			assertThat(command.lat()).isEqualTo(LAT);
			assertThat(command.lng()).isEqualTo(LNG);
		});
	}

	@Test
	@DisplayName("같은 구성의 코스는 같은 candidateKey를 갖는다")
	void candidateKeyIsStable() {
		givenAreaCenterAndCandidates();
		when(savedRepository.findTop200ByMemberIdOrderByIdDesc(MEMBER)).thenReturn(List.of());
		RecommendCoursesRequest request = new RecommendCoursesRequest(null, "SUNGSHIN");

		List<String> first = service.recommend(MEMBER, request).courses().stream().map(c -> c.candidateKey()).toList();
		List<String> second = service.recommend(MEMBER, request).courses().stream().map(c -> c.candidateKey()).toList();

		assertThat(first).isEqualTo(second).doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("기준 장소로 추천하면 그 장소가 모든 코스의 첫 장소이고 보관함 번호를 돌려준다")
	void recommendsByAnchor() {
		SavedPlace anchor = SavedPlace.create(MEMBER, PlaceProvider.KAKAO, "anchor", "내 장소", "주소", null, "음식점",
			"CE7", LAT, LNG, null);
		when(savedRepository.findByIdAndMemberId(5L, MEMBER)).thenReturn(Optional.of(anchor));
		when(savedRepository.findTop200ByMemberIdOrderByIdDesc(MEMBER)).thenReturn(List.of(anchor));
		givenAreaCenterAndCandidates();

		CourseRecommendationResponse response = service.recommend(MEMBER, new RecommendCoursesRequest(5L, null));

		assertThat(response.status()).isEqualTo(Status.OK);
		assertThat(response.courses()).allSatisfy(course ->
			assertThat(course.places().get(0).providerPlaceId()).isEqualTo("anchor"));
		assertThat(search.commands).extracting(PlaceSearchCommand::query).doesNotContain("성신여대입구역");
		assertThat(search.commands).allSatisfy(command -> {
			assertThat(command.lat()).isEqualTo(LAT);
			assertThat(command.lng()).isEqualTo(LNG);
		});
	}

	@Test
	@DisplayName("기준 장소와 지역을 둘 다 보내거나 둘 다 안 보내면 거절한다")
	void requiresExactlyOneStart() {
		assertThatThrownBy(() -> service.recommend(MEMBER, new RecommendCoursesRequest(1L, "잠실")))
			.satisfies(thrown -> assertThat(code(thrown)).isEqualTo("RECOMMENDATION-0400"));
		assertThatThrownBy(() -> service.recommend(MEMBER, new RecommendCoursesRequest(null, " ")))
			.satisfies(thrown -> assertThat(code(thrown)).isEqualTo("RECOMMENDATION-0400"));
		assertThat(search.commands).isEmpty();
	}

	@Test
	@DisplayName("지원하지 않는 지역은 지역을 넓히지 않고 안내와 함께 거절한다")
	void rejectsUnsupportedArea() {
		assertThatThrownBy(() -> service.recommend(MEMBER, new RecommendCoursesRequest(null, "강남")))
			.satisfies(thrown -> assertThat(code(thrown)).isEqualTo("RECOMMENDATION-0401"));
		assertThat(search.commands).isEmpty();
	}

	@Test
	@DisplayName("내 보관함에 없거나 다른 회원의 기준 장소는 404다")
	void anchorMustBeMine() {
		when(savedRepository.findByIdAndMemberId(anyLong(), anyLong())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.recommend(MEMBER, new RecommendCoursesRequest(99L, null)))
			.satisfies(thrown -> assertThat(code(thrown)).isEqualTo("PLACE-0404"));
		assertThat(search.commands).isEmpty();
	}

	@Test
	@DisplayName("후보가 부족하면 NOT_ENOUGH_CANDIDATES와 빈 목록을 돌려준다")
	void notEnoughCandidates() {
		search.results.put("잠실역", List.of(candidate("center", "SW8", 0, 0)));
		search.results.put("카페", List.of(candidate("c1", "CE7", 0.001, 0)));
		when(savedRepository.findTop200ByMemberIdOrderByIdDesc(MEMBER)).thenReturn(List.of());

		CourseRecommendationResponse response = service.recommend(MEMBER, new RecommendCoursesRequest(null, "잠실"));

		assertThat(response.status()).isEqualTo(Status.NOT_ENOUGH_CANDIDATES);
		assertThat(response.courses()).isEmpty();
	}

	@Test
	@DisplayName("카카오 검색이 실패하면 그대로 실패하고, 지역 중심을 못 찾아도 실패한다")
	void propagatesSearchFailures() {
		when(savedRepository.findTop200ByMemberIdOrderByIdDesc(MEMBER)).thenReturn(List.of());
		search.failure = new PlaceUpstreamException(Reason.RATE_LIMIT, "limit");
		assertThatThrownBy(() -> service.recommend(MEMBER, new RecommendCoursesRequest(null, "혜화"))).isSameAs(search.failure);

		search.failure = null;
		assertThatThrownBy(() -> service.recommend(MEMBER, new RecommendCoursesRequest(null, "혜화")))
			.isInstanceOf(PlaceUpstreamException.class);
	}

	@Test
	@DisplayName("지원 지역은 화면 이름이나 영문 이름으로 찾는다")
	void findsAreasByNameOrCode() {
		assertThat(SupportedArea.find("서촌")).contains(SupportedArea.SEOCHON);
		assertThat(SupportedArea.find(" yeonnam ")).contains(SupportedArea.YEONNAM);
		assertThat(SupportedArea.find("성신여대입구역")).contains(SupportedArea.SUNGSHIN);
		assertThat(SupportedArea.find("강남")).isEmpty();
		assertThat(SupportedArea.find(null)).isEmpty();
	}

	private static class FakeSearch implements PlaceSearchClient {

		private final List<PlaceSearchCommand> commands = new ArrayList<>();
		private final Map<String, List<PlaceCandidate>> results = new HashMap<>();
		private RuntimeException failure;

		@Override
		public PlaceSearchResult search(PlaceSearchCommand command) {
			commands.add(command);
			if (failure != null) {
				throw failure;
			}
			return new PlaceSearchResult(results.getOrDefault(command.query(), List.of()), 1, false);
		}
	}
}
