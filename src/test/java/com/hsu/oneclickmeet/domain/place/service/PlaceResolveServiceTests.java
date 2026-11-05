package com.hsu.oneclickmeet.domain.place.service;

import com.hsu.oneclickmeet.domain.place.client.PlaceCandidate;
import com.hsu.oneclickmeet.domain.place.client.PlaceNameExtractor;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchClient;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchCommand;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchResult;
import com.hsu.oneclickmeet.domain.place.dto.response.PlaceResolveResponse;
import com.hsu.oneclickmeet.domain.place.dto.response.PlaceResolveResponse.Status;
import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException;
import com.hsu.oneclickmeet.domain.place.exception.PlaceUpstreamException.Reason;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaceResolveServiceTests {

	private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

	private final FakeExtractor extractor = new FakeExtractor();
	private final FakeSearch search = new FakeSearch();
	private final PlaceResolveService service = new PlaceResolveService(extractor, search);

	private static PlaceCandidate candidate(String id, String name) {
		return new PlaceCandidate(id, name, "주소", null, null, "CE7", 37.5, 127.0, null);
	}

	@Test
	@DisplayName("AI가 뽑은 이름마다 카카오를 검색해 중복 없이 후보로 합친다")
	void searchesEachNameAndMergesUniqueCandidates() {
		extractor.names = List.of("연우재", "카머");
		search.results.put("연우재", List.of(candidate("1", "연우재"), candidate("2", "연우재 2호점")));
		search.results.put("카머", List.of(candidate("2", "연우재 2호점"), candidate("3", "카머")));

		PlaceResolveResponse response = service.resolveLink("공유 텍스트");

		assertThat(response.status()).isEqualTo(Status.CANDIDATES_FOUND);
		assertThat(response.candidates()).extracting(c -> c.providerPlaceId()).containsExactly("1", "2", "3");
		assertThat(search.commands).extracting(PlaceSearchCommand::query).containsExactly("연우재", "카머");
		assertThat(search.commands).allSatisfy(command -> {
			assertThat(command.page()).isEqualTo(1);
			assertThat(command.size()).isEqualTo(5);
		});
	}

	@Test
	@DisplayName("이름은 3개까지만 검색하고 후보는 10개까지만 돌려준다")
	void limitsSearchesAndCandidates() {
		extractor.names = List.of("a", "b", "c", "d");
		for (String name : List.of("a", "b", "c")) {
			List<PlaceCandidate> found = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
				found.add(candidate(name + i, name + i));
			}
			search.results.put(name, found);
		}

		PlaceResolveResponse response = service.resolveLink("x");

		assertThat(search.commands).hasSize(3);
		assertThat(response.candidates()).hasSize(10);
	}

	@Test
	@DisplayName("AI 서버를 쓸 수 없으면 규칙으로 이름을 찾아 검색한다")
	void fallsBackToHeuristicWhenAiFails() {
		extractor.failure = new PlaceUpstreamException(Reason.NETWORK, "down");
		search.results.put("연우재", List.of(candidate("1", "연우재")));

		PlaceResolveResponse response = service.resolveLink("[네이버지도]\n연우재\nhttps://naver.me/abc");

		assertThat(response.status()).isEqualTo(Status.CANDIDATES_FOUND);
		assertThat(search.commands).extracting(PlaceSearchCommand::query).containsExactly("연우재");
	}

	@Test
	@DisplayName("이름을 못 찾거나 검색 결과가 없으면 NEEDS_SEARCH다")
	void needsSearchWhenNothingFound() {
		extractor.names = List.of();
		assertThat(service.resolveLink("x").status()).isEqualTo(Status.NEEDS_SEARCH);
		assertThat(search.commands).isEmpty();

		extractor.names = List.of("없는곳");
		assertThat(service.resolveLink("x").status()).isEqualTo(Status.NEEDS_SEARCH);
	}

	@Test
	@DisplayName("카카오 검색이 실패하면 그대로 실패한다")
	void propagatesKakaoFailure() {
		extractor.names = List.of("연우재");
		search.failure = new PlaceUpstreamException(Reason.RATE_LIMIT, "limit");

		assertThatThrownBy(() -> service.resolveLink("x")).isSameAs(search.failure);
	}

	@Test
	@DisplayName("이미지에서 이름을 뽑아 후보를 찾는다")
	void resolvesImage() {
		extractor.names = List.of("연우재");
		search.results.put("연우재", List.of(candidate("1", "연우재")));

		PlaceResolveResponse response = service.resolveImage(PNG, "image/png");

		assertThat(response.status()).isEqualTo(Status.CANDIDATES_FOUND);
		assertThat(extractor.imageCalls).isEqualTo(1);
	}

	@Test
	@DisplayName("이미지 분석을 쓸 수 없으면 오류 대신 NEEDS_SEARCH다")
	void imageNeedsSearchWhenAiFails() {
		extractor.failure = new PlaceUpstreamException(Reason.AUTH, "not configured");

		PlaceResolveResponse response = service.resolveImage(PNG, "image/png");

		assertThat(response.status()).isEqualTo(Status.NEEDS_SEARCH);
		assertThat(search.commands).isEmpty();
	}

	@Test
	@DisplayName("이미지가 비었거나 형식·크기·내용이 맞지 않으면 AI 서버를 호출하지 않고 400이다")
	void rejectsInvalidImages() {
		byte[] tooLarge = new byte[(int) PlaceResolveService.MAX_IMAGE_BYTES + 1];
		System.arraycopy(PNG, 0, tooLarge, 0, PNG.length);

		for (var invalid : List.of(
				new Object[] {new byte[0], "image/png"},
				new Object[] {PNG, "text/plain"},
				new Object[] {PNG, null},
				new Object[] {PNG, "image/jpeg"},
				new Object[] {new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13}, "image/png"},
				new Object[] {tooLarge, "image/png"})) {
			assertThatThrownBy(() -> service.resolveImage((byte[]) invalid[0], (String) invalid[1]))
				.isInstanceOf(BusinessException.class);
		}
		assertThat(extractor.imageCalls).isZero();
	}

	private static class FakeExtractor implements PlaceNameExtractor {

		private List<String> names = List.of();
		private RuntimeException failure;
		private int imageCalls;

		@Override
		public List<String> fromText(String sharedText) {
			if (failure != null) {
				throw failure;
			}
			return names;
		}

		@Override
		public List<String> fromImage(byte[] image, String contentType) {
			imageCalls++;
			if (failure != null) {
				throw failure;
			}
			return names;
		}
	}

	private static class FakeSearch implements PlaceSearchClient {

		private final List<PlaceSearchCommand> commands = new ArrayList<>();
		private final java.util.Map<String, List<PlaceCandidate>> results = new java.util.HashMap<>();
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
