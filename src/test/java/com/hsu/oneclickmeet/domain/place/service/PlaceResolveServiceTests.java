package com.hsu.oneclickmeet.domain.place.service;

import com.hsu.oneclickmeet.domain.place.client.PlaceCandidate;
import com.hsu.oneclickmeet.domain.place.client.PlaceRecognizer;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchClient;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchCommand;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchResult;
import com.hsu.oneclickmeet.domain.place.client.Recognition;
import com.hsu.oneclickmeet.domain.place.client.RecognitionImage;
import com.hsu.oneclickmeet.domain.place.client.Recognition.RecognizedPlace;
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

	private final FakeRecognizer recognizer = new FakeRecognizer();
	private final FakeSearch search = new FakeSearch();
	private final PlaceResolveService service = new PlaceResolveService(recognizer, search);

	private static PlaceCandidate candidate(String id, String name) {
		return new PlaceCandidate(id, name, "주소", null, null, "CE7", 37.5, 127.0, null);
	}

	private static RecognizedPlace recognized(String id, String name) {
		return new RecognizedPlace(candidate(id, name), "EXACT", List.of("이름 일치"),
				List.of(new Recognition.Tag("음식이 맛있어요", 3)));
	}

	@Test
	@DisplayName("AI 서버가 준 후보를 카카오 재검색 없이 매칭 정보와 함께 그대로 돌려준다")
	void usesAiCandidatesWithoutSearching() {
		recognizer.result = new Recognition("연우재", List.of(recognized("1", "연우재")));

		PlaceResolveResponse response = service.resolveLink("공유 텍스트");

		assertThat(response.status()).isEqualTo(Status.CANDIDATES_FOUND);
		assertThat(response.extractedQuery()).isEqualTo("연우재");
		assertThat(response.candidates()).hasSize(1);
		assertThat(response.candidates().get(0).matchType()).isEqualTo("EXACT");
		assertThat(response.candidates().get(0).tags()).hasSize(1);
		assertThat(search.commands).isEmpty();
	}

	@Test
	@DisplayName("AI 서버를 쓸 수 없으면 규칙으로 이름을 찾아 카카오를 검색한다")
	void fallsBackToHeuristicWhenAiFails() {
		recognizer.failure = new PlaceUpstreamException(Reason.NETWORK, "down");
		search.results.put("연우재", List.of(candidate("1", "연우재")));

		PlaceResolveResponse response = service.resolveLink("[네이버지도]\n연우재\nhttps://naver.me/abc");

		assertThat(response.status()).isEqualTo(Status.CANDIDATES_FOUND);
		assertThat(response.candidates().get(0).matchType()).isNull();
		assertThat(search.commands).extracting(PlaceSearchCommand::query).containsExactly("연우재");
	}

	@Test
	@DisplayName("폴백 검색은 이름 3개까지, 후보는 10개까지이고 중복은 합친다")
	void fallbackLimitsSearchesAndCandidates() {
		recognizer.failure = new PlaceUpstreamException(Reason.SERVER_ERROR, "down");
		for (String name : List.of("연우재", "카머", "오지다")) {
			List<PlaceCandidate> found = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
				found.add(candidate(name + i, name + i));
			}
			search.results.put(name, found);
		}
		search.results.put("카머", List.of(candidate("연우재0", "연우재0")));

		PlaceResolveResponse response = service.resolveLink("1. 연우재\n2. 카머\n3. 오지다\n4. 다른곳");

		assertThat(search.commands.size()).isLessThanOrEqualTo(3);
		assertThat(response.candidates().size()).isLessThanOrEqualTo(10);
		assertThat(response.candidates()).extracting(c -> c.providerPlaceId()).doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("후보가 없으면 NEEDS_SEARCH와 검색어를 200으로 돌려준다")
	void needsSearchWhenNothingFound() {
		recognizer.result = new Recognition("연남 카페", List.of());

		PlaceResolveResponse response = service.resolveLink("x");

		assertThat(response.status()).isEqualTo(Status.NEEDS_SEARCH);
		assertThat(response.extractedQuery()).isEqualTo("연남 카페");
		assertThat(search.commands).isEmpty();
	}

	@Test
	@DisplayName("폴백 중 카카오 검색이 실패하면 그대로 실패한다")
	void propagatesKakaoFailureOnFallback() {
		recognizer.failure = new PlaceUpstreamException(Reason.NETWORK, "down");
		search.failure = new PlaceUpstreamException(Reason.RATE_LIMIT, "limit");

		assertThatThrownBy(() -> service.resolveLink("[네이버지도]\n연우재\nhttps://naver.me/abc")).isSameAs(search.failure);
	}

	@Test
	@DisplayName("이미지에서 후보를 찾는다")
	void resolvesImage() {
		recognizer.result = new Recognition(null, List.of(recognized("1", "연우재")));

		PlaceResolveResponse response = service.resolveImage(new RecognitionImage(PNG, "image/png"), List.of());

		assertThat(response.status()).isEqualTo(Status.CANDIDATES_FOUND);
		assertThat(recognizer.imageCalls).isEqualTo(1);
	}

	@Test
	@DisplayName("추가 캡처를 원본과 함께 AI 서버로 전달한다")
	void passesExtraImagesToRecognizer() {
		recognizer.result = new Recognition(null, List.of(recognized("1", "연우재")));
		RecognitionImage extra = new RecognitionImage(PNG, "image/png");

		service.resolveImage(new RecognitionImage(PNG, "image/png"), List.of(extra, extra, extra, extra));

		assertThat(recognizer.lastExtraImages).hasSize(PlaceResolveService.MAX_EXTRA_IMAGES);
	}

	@Test
	@DisplayName("추가 캡처가 5장이거나 하나라도 올바르지 않으면 AI 서버를 호출하지 않고 400이다")
	void rejectsInvalidExtraImages() {
		RecognitionImage image = new RecognitionImage(PNG, "image/png");
		RecognitionImage bad = new RecognitionImage(PNG, "image/jpeg");

		assertThatThrownBy(() -> service.resolveImage(image, List.of(image, image, image, image, image)))
			.isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> service.resolveImage(image, List.of(image, bad)))
			.isInstanceOf(BusinessException.class);
		assertThat(recognizer.imageCalls).isZero();
	}

	@Test
	@DisplayName("이미지 분석을 쓸 수 없으면 오류 대신 NEEDS_SEARCH다")
	void imageNeedsSearchWhenAiFails() {
		recognizer.failure = new PlaceUpstreamException(Reason.AUTH, "not configured");

		PlaceResolveResponse response = service.resolveImage(new RecognitionImage(PNG, "image/png"), List.of());

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
			assertThatThrownBy(() -> service.resolveImage(new RecognitionImage((byte[]) invalid[0], (String) invalid[1]), List.of()))
				.isInstanceOf(BusinessException.class);
		}
		assertThat(recognizer.imageCalls).isZero();
	}

	private static class FakeRecognizer implements PlaceRecognizer {

		private Recognition result = new Recognition(null, List.of());
		private RuntimeException failure;
		private int imageCalls;
		private List<RecognitionImage> lastExtraImages;

		@Override
		public Recognition recognizeText(String sharedText) {
			if (failure != null) {
				throw failure;
			}
			return result;
		}

		@Override
		public Recognition recognizeImage(RecognitionImage image, List<RecognitionImage> extraImages) {
			imageCalls++;
			lastExtraImages = extraImages;
			return recognizeText("");
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
