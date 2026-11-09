package com.hsu.oneclickmeet.domain.place.service;

import com.hsu.oneclickmeet.domain.place.client.PlaceCandidate;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchClient;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchCommand;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchResult;
import com.hsu.oneclickmeet.domain.place.dto.request.PlaceSearchRequest;
import com.hsu.oneclickmeet.domain.place.dto.response.PlaceSearchResponse;
import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException;
import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException.Reason;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaceSearchServiceTests {

	private static final PlaceCandidate CANDIDATE = new PlaceCandidate(
		"123", "예시 카페", "서울 성북구 삼선동 1", null, "음식점 > 카페", "CE7", 37.5822, 127.0105, "http://place.map.kakao.com/123");

	private final FakeClient client = new FakeClient();
	private final PlaceSearchService service = new PlaceSearchService(client);

	private static PlaceSearchRequest request(String query, int page, int size) {
		String cursor = page == 1 ? null : PlaceSearchCursor.encode(query, size, page);
		return new PlaceSearchRequest(query, cursor, size);
	}

	@Test
	@DisplayName("검색어 앞뒤 공백을 제거하고 커서의 쪽 번호·size를 클라이언트에 전달하며 위치는 보내지 않는다")
	void trimsQueryAndPassesParametersToClient() {
		client.result = new PlaceSearchResult(List.of(CANDIDATE), 2, true);

		service.search(new PlaceSearchRequest("  한성대입구역 카페  ", PlaceSearchCursor.encode("한성대입구역 카페", 5, 2), 5));

		assertThat(client.commands).singleElement().satisfies(command -> {
			assertThat(command.query()).isEqualTo("한성대입구역 카페");
			assertThat(command.page()).isEqualTo(2);
			assertThat(command.size()).isEqualTo(5);
			assertThat(command.lat()).isNull();
			assertThat(command.lng()).isNull();
		});
	}

	@Test
	@DisplayName("후보를 응답 DTO로 변환한다")
	void mapsCandidatesToResponse() {
		client.result = new PlaceSearchResult(List.of(CANDIDATE), 1, false);

		PlaceSearchResponse response = service.search(request("카페", 1, 10));

		assertThat(response.items()).singleElement().satisfies(candidate -> {
			assertThat(candidate.providerPlaceId()).isEqualTo("123");
			assertThat(candidate.name()).isEqualTo("예시 카페");
			assertThat(candidate.roadAddress()).isNull();
			assertThat(candidate.lat()).isEqualTo(37.5822);
			assertThat(candidate.lng()).isEqualTo(127.0105);
		});
	}

	@Test
	@DisplayName("cursor·size 생략 시 기본값(1쪽, 10건)을 사용한다")
	void usesDefaultPageAndSize() {
		client.result = new PlaceSearchResult(List.of(), 1, false);

		service.search(new PlaceSearchRequest("카페", null, null));

		assertThat(client.commands).singleElement().satisfies(command -> {
			assertThat(command.page()).isEqualTo(1);
			assertThat(command.size()).isEqualTo(10);
		});
	}

	@Test
	@DisplayName("결과가 없으면 빈 목록을 돌려준다")
	void returnsEmptyCandidatesWhenClientHasNone() {
		client.result = new PlaceSearchResult(List.of(), 1, false);

		PlaceSearchResponse response = service.search(request("없는장소", 1, 10));

		assertThat(response.items()).isEmpty();
		assertThat(response.hasNext()).isFalse();
	}

	@Test
	@DisplayName("상한(45건) 안이고 다음 쪽이 있으면 hasNext는 true다")
	void hasNextIsTrueWhenWithinLimitAndClientHasNext() {
		client.result = new PlaceSearchResult(List.of(CANDIDATE), 1, true);

		assertThat(service.search(request("카페", 1, 10)).hasNext()).isTrue();
		assertThat(service.search(request("카페", 4, 10)).hasNext()).isTrue();
	}

	@Test
	@DisplayName("상한에 닿는 마지막 쪽은 카카오가 다음 쪽이 있다고 해도 hasNext가 false다")
	void hasNextIsFalseOnLastPageEvenIfClientSaysMore() {
		client.result = new PlaceSearchResult(List.of(CANDIDATE), 5, true);

		PlaceSearchResponse tenPerPage = service.search(request("카페", 5, 10));
		PlaceSearchResponse fifteenPerPage = service.search(request("카페", 3, 15));

		assertThat(tenPerPage.hasNext()).isFalse();
		assertThat(fifteenPerPage.hasNext()).isFalse();
		assertThat(client.commands).hasSize(2);
	}

	@Test
	@DisplayName("마지막 쪽의 일부(41~45번째)는 카카오를 호출해 받는다")
	void callsClientForPartialLastPage() {
		client.result = new PlaceSearchResult(List.of(CANDIDATE), 5, false);

		PlaceSearchResponse response = service.search(request("카페", 5, 10));

		assertThat(client.commands).hasSize(1);
		assertThat(response.items()).hasSize(1);
	}

	@Test
	@DisplayName("상한을 넘는 쪽은 카카오를 호출하지 않고 빈 결과를 돌려준다")
	void returnsEmptyWithoutCallingClientWhenBeyondLimit() {
		PlaceSearchResponse tenPerPage = service.search(request("카페", 6, 10));
		PlaceSearchResponse fifteenPerPage = service.search(request("카페", 4, 15));

		assertThat(client.commands).isEmpty();
		assertThat(tenPerPage).isEqualTo(PlaceSearchResponse.empty());
		assertThat(fifteenPerPage).isEqualTo(PlaceSearchResponse.empty());
	}

	@Test
	@DisplayName("매우 큰 page 값도 오버플로 없이 빈 결과로 처리한다")
	void handlesHugePageWithoutOverflow() {
		PlaceSearchResponse response = service.search(request("카페", Integer.MAX_VALUE, 15));

		assertThat(response.items()).isEmpty();
		assertThat(response.hasNext()).isFalse();
		assertThat(client.commands).isEmpty();
	}

	@Test
	@DisplayName("외부 호출 실패 예외는 그대로 전달한다")
	void propagatesUpstreamException() {
		client.failure = new PlaceUpstreamException(Reason.RATE_LIMIT, "한도 초과");

		assertThatThrownBy(() -> service.search(request("카페", 1, 10)))
			.isInstanceOf(PlaceUpstreamException.class)
			.satisfies(thrown -> assertThat(((PlaceUpstreamException) thrown).getReason()).isEqualTo(Reason.RATE_LIMIT));
	}

	@Test
	@DisplayName("다음 쪽이 있으면 nextCursor를 주고, 그 커서로 다음 쪽(2쪽)을 요청한다")
	void nextCursorLeadsToNextPage() {
		client.result = new PlaceSearchResult(List.of(CANDIDATE), 1, true);

		PlaceSearchResponse first = service.search(new PlaceSearchRequest("카페", null, 10));
		service.search(new PlaceSearchRequest("카페", first.nextCursor(), 10));

		assertThat(first.hasNext()).isTrue();
		assertThat(first.nextCursor()).isNotBlank();
		assertThat(client.commands).extracting(PlaceSearchCommand::page).containsExactly(1, 2);
	}

	@Test
	@DisplayName("다음 쪽이 없으면 nextCursor는 null이다")
	void nextCursorIsNullWithoutNextPage() {
		client.result = new PlaceSearchResult(List.of(CANDIDATE), 1, false);

		PlaceSearchResponse response = service.search(request("카페", 1, 10));

		assertThat(response.hasNext()).isFalse();
		assertThat(response.nextCursor()).isNull();
	}

	@Test
	@DisplayName("검색어 앞뒤 공백이 달라도 같은 검색으로 보고 커서를 받아들인다")
	void cursorIgnoresSurroundingWhitespaceOfQuery() {
		client.result = new PlaceSearchResult(List.of(CANDIDATE), 1, true);

		PlaceSearchResponse first = service.search(new PlaceSearchRequest(" 카페 ", null, 10));
		service.search(new PlaceSearchRequest("카페", first.nextCursor(), 10));

		assertThat(client.commands).extracting(PlaceSearchCommand::page).containsExactly(1, 2);
	}

	@Test
	@DisplayName("다른 검색어나 다른 size에서 만든 커서, 깨진 커서는 400으로 거절한다")
	void rejectsForeignOrMalformedCursor() {
		String cursor = PlaceSearchCursor.encode("카페", 10, 2);
		String firstPage = PlaceSearchCursor.encode("카페", 10, 1);
		String notNumber = java.util.Base64.getUrlEncoder().withoutPadding()
			.encodeToString("ps1|x|10|abc".getBytes(java.nio.charset.StandardCharsets.UTF_8));

		for (PlaceSearchRequest request : List.of(
				new PlaceSearchRequest("식당", cursor, 10),
				new PlaceSearchRequest("카페", cursor, 5),
				new PlaceSearchRequest("카페", firstPage, 10),
				new PlaceSearchRequest("카페", notNumber, 10),
				new PlaceSearchRequest("카페", "!!!", 10),
				new PlaceSearchRequest("카페", "a".repeat(201), 10))) {
			assertThatThrownBy(() -> service.search(request))
				.isInstanceOf(BusinessException.class)
				.satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
					.isEqualTo(CommonErrorCode.BAD_REQUEST));
		}
		assertThat(client.commands).isEmpty();
	}

	private static class FakeClient implements PlaceSearchClient {

		private final List<PlaceSearchCommand> commands = new ArrayList<>();
		private PlaceSearchResult result = new PlaceSearchResult(List.of(), 1, false);
		private RuntimeException failure;

		@Override
		public PlaceSearchResult search(PlaceSearchCommand command) {
			commands.add(command);
			if (failure != null) {
				throw failure;
			}
			return result;
		}
	}
}
