package com.hsu.oneclickmeet.domain.place.service;

import com.hsu.oneclickmeet.domain.place.client.PlaceCandidate;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchClient;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchCommand;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchResult;
import com.hsu.oneclickmeet.domain.place.dto.response.NearbyPlaceResponse;
import com.hsu.oneclickmeet.domain.place.dto.response.NearbyPlacesResponse;
import com.hsu.oneclickmeet.domain.place.entity.PlaceProvider;
import com.hsu.oneclickmeet.domain.place.entity.SavedPlace;
import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException;
import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException.Reason;
import com.hsu.oneclickmeet.domain.place.repository.SavedPlaceRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NearbyPlaceServiceTests {

	private static final long MEMBER = 7L;
	private static final double LAT = 37.5894;
	private static final double LNG = 127.0044;

	private final SavedPlaceRepository repository = mock(SavedPlaceRepository.class);
	private final FakeSearch search = new FakeSearch();
	private final NearbyPlaceService service = new NearbyPlaceService(repository, search,
		mock(PlatformTransactionManager.class));

	private static SavedPlace base() {
		return SavedPlace.create(MEMBER, PlaceProvider.KAKAO, "base", "연우재", "주소", null, "카페", "CE7", LAT, LNG, null);
	}

	/** dLat 0.001도는 약 111m다. */
	private static PlaceCandidate candidate(String id, double dLat) {
		return new PlaceCandidate(id, "장소 " + id, "주소", null, "카페", "CE7", LAT + dLat, LNG, null);
	}

	@Test
	@DisplayName("저장된 장소의 좌표로 종류별 검색어를 보내고, 1km 안의 장소를 가까운 순으로 거리와 함께 돌려준다")
	void returnsNearbyPlacesSortedByDistance() {
		when(repository.findByIdAndMemberId(5L, MEMBER)).thenReturn(Optional.of(base()));
		search.result = List.of(candidate("far", 0.008), candidate("near", 0.001), candidate("out", 0.02));

		NearbyPlacesResponse response = service.nearby(MEMBER, 5L, NearbyCategory.RESTAURANT);

		assertThat(response.savedPlaceId()).isEqualTo(5L);
		assertThat(response.category()).isEqualTo(NearbyCategory.RESTAURANT);
		assertThat(response.items()).extracting(item -> item.place().providerPlaceId()).containsExactly("near", "far");
		assertThat(response.items()).extracting(NearbyPlaceResponse::distanceMeters).allSatisfy(meters ->
			assertThat(meters).isBetween(0, 1000));
		assertThat(response.items().get(0).distanceMeters()).isBetween(105, 117);
		PlaceSearchCommand command = search.commands.get(0);
		assertThat(command.query()).isEqualTo("맛집");
		assertThat(command.lat()).isEqualTo(LAT);
		assertThat(command.lng()).isEqualTo(LNG);
		assertThat(command.page()).isEqualTo(1);
		assertThat(command.size()).isEqualTo(15);
	}

	@Test
	@DisplayName("기준 장소 자신은 결과에서 뺀다")
	void excludesBasePlace() {
		when(repository.findByIdAndMemberId(5L, MEMBER)).thenReturn(Optional.of(base()));
		search.result = List.of(candidate("base", 0.0), candidate("near", 0.001));

		assertThat(service.nearby(MEMBER, 5L, NearbyCategory.CAFE).items())
			.extracting(item -> item.place().providerPlaceId()).containsExactly("near");
	}

	@Test
	@DisplayName("주변에 없으면 빈 목록이다")
	void emptyWhenNothingNearby() {
		when(repository.findByIdAndMemberId(5L, MEMBER)).thenReturn(Optional.of(base()));

		assertThat(service.nearby(MEMBER, 5L, NearbyCategory.PARK).items()).isEmpty();
	}

	@Test
	@DisplayName("없거나 다른 회원의 장소면 카카오를 부르지 않고 404다")
	void notFoundForMissingOrOthersPlace() {
		when(repository.findByIdAndMemberId(anyLong(), anyLong())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.nearby(MEMBER, 99L, NearbyCategory.CAFE))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode().getCode()).isEqualTo("PLACE-0404"));
		assertThat(search.commands).isEmpty();
	}

	@Test
	@DisplayName("카카오 검색이 실패하면 그대로 실패한다")
	void propagatesSearchFailure() {
		when(repository.findByIdAndMemberId(5L, MEMBER)).thenReturn(Optional.of(base()));
		search.failure = new PlaceUpstreamException(Reason.NETWORK, "down");

		assertThatThrownBy(() -> service.nearby(MEMBER, 5L, NearbyCategory.CAFE)).isSameAs(search.failure);
	}

	private static class FakeSearch implements PlaceSearchClient {

		private final List<PlaceSearchCommand> commands = new ArrayList<>();
		private List<PlaceCandidate> result = List.of();
		private RuntimeException failure;

		@Override
		public PlaceSearchResult search(PlaceSearchCommand command) {
			commands.add(command);
			if (failure != null) {
				throw failure;
			}
			return new PlaceSearchResult(result, 1, false);
		}
	}
}
