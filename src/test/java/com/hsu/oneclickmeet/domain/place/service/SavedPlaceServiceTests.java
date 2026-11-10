package com.hsu.oneclickmeet.domain.place.service;

import com.hsu.oneclickmeet.domain.place.dto.request.SaveSavedPlaceRequest;
import com.hsu.oneclickmeet.domain.place.dto.response.SavedPlaceListResponse;
import com.hsu.oneclickmeet.domain.place.dto.response.SavedPlaceResponse;
import com.hsu.oneclickmeet.domain.place.service.SavedPlaceService.SaveResult;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class SavedPlaceServiceTests {

	private static final long MEMBER = 820_001L;
	private static final long OTHER = 820_002L;

	@Autowired
	private SavedPlaceService service;
	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from saved_places where member_id in (?, ?)", MEMBER, OTHER);
	}

	private static SaveSavedPlaceRequest request(String providerPlaceId, String groupCode) {
		return new SaveSavedPlaceRequest(providerPlaceId, "장소 " + providerPlaceId, "서울 성북구 삼선동 1", "도로명",
				"음식점 > 카페", groupCode, 37.5822, 127.0105, "http://place.map.kakao.com/" + providerPlaceId, null);
	}

	private void age(long savedPlaceId, String createdAt) {
		jdbc.update("update saved_places set created_at = ? where id = ?", Timestamp.from(Instant.parse(createdAt)),
				savedPlaceId);
	}

	private static String errorCode(Throwable thrown) {
		assertThat(thrown).isInstanceOf(BusinessException.class);
		return ((BusinessException) thrown).getErrorCode().getCode();
	}

	@Test
	@DisplayName("처음 저장하면 새 항목을 만들고 created=true를 돌려준다")
	void createsNewPlace() {
		SaveResult result = service.save(MEMBER, request("100", "CE7"));

		assertThat(result.created()).isTrue();
		assertThat(result.place().savedPlaceId()).isPositive();
		assertThat(result.place().providerPlaceId()).isEqualTo("100");
		assertThat(result.place().name()).isEqualTo("장소 100");
		assertThat(result.place().savedAt()).isNotNull();
	}

	@Test
	@DisplayName("이미 저장한 장소를 다시 저장하면 기존 항목을 돌려주고 새로 만들지 않는다")
	void returnsExistingPlaceWhenSavedAgain() {
		SaveResult first = service.save(MEMBER, request("100", "CE7"));
		SaveResult second = service.save(MEMBER, request(" 100 ", "CE7"));

		assertThat(second.created()).isFalse();
		assertThat(second.place().savedPlaceId()).isEqualTo(first.place().savedPlaceId());
		assertThat(jdbc.queryForObject("select count(*) from saved_places where member_id = ?", Integer.class, MEMBER))
				.isEqualTo(1);
	}

	@Test
	@DisplayName("tags를 저장하고 저장·조회 응답에 그대로 돌려준다")
	void savesAndReturnsTags() {
		SaveSavedPlaceRequest withTags = new SaveSavedPlaceRequest("100", "장소 100", "주소", null, null, "CE7", 37.5, 127.0,
				null, List.of(new SaveSavedPlaceRequest.TagRequest("아늑해요", 12),
						new SaveSavedPlaceRequest.TagRequest("뷰가 좋아요", 7)));

		SaveResult saved = service.save(MEMBER, withTags);
		SavedPlaceResponse detail = service.detail(MEMBER, saved.place().savedPlaceId());

		assertThat(saved.place().tags()).extracting(tag -> tag.label() + ":" + tag.mentionCount())
				.containsExactly("아늑해요:12", "뷰가 좋아요:7");
		assertThat(detail.tags()).isEqualTo(saved.place().tags());
	}

	@Test
	@DisplayName("tags가 없거나 비어 있어도 저장되고 응답의 tags는 빈 목록이다")
	void savesWithoutTags() {
		SaveSavedPlaceRequest empty = new SaveSavedPlaceRequest("101", "장소 101", "주소", null, null, null, 37.5, 127.0,
				null, List.of());

		assertThat(service.save(MEMBER, request("100", "CE7")).place().tags()).isEmpty();
		SaveResult saved = service.save(MEMBER, empty);
		assertThat(saved.place().tags()).isEmpty();
		assertThat(jdbc.queryForObject("select tags from saved_places where id = ?", String.class,
				saved.place().savedPlaceId())).isNull();
	}

	@Test
	@DisplayName("다시 저장해도 처음 저장한 값과 저장 시각은 바뀌지 않는다")
	void keepsOriginalValuesWhenSavedAgain() {
		SaveResult first = service.save(MEMBER, request("100", "CE7"));
		SaveSavedPlaceRequest changed = new SaveSavedPlaceRequest("100", "바뀐 이름", "바뀐 주소", null, null, null, 1.0, 2.0, null, null);

		SaveResult second = service.save(MEMBER, changed);

		assertThat(second.place().name()).isEqualTo("장소 100");
		assertThat(second.place().savedAt()).isEqualTo(first.place().savedAt());
	}

	@Test
	@DisplayName("다른 회원은 같은 장소를 각자 저장한다")
	void savesSamePlaceSeparatelyForDifferentMembers() {
		SaveResult mine = service.save(MEMBER, request("100", "CE7"));
		SaveResult others = service.save(OTHER, request("100", "CE7"));

		assertThat(mine.created()).isTrue();
		assertThat(others.created()).isTrue();
		assertThat(others.place().savedPlaceId()).isNotEqualTo(mine.place().savedPlaceId());
	}

	@Test
	@DisplayName("같은 장소를 동시에 저장해도 한 건만 만들어지고 모든 요청이 같은 항목을 돌려준다")
	void handlesConcurrentSaveOfSamePlace() throws Exception {
		int threads = 6;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<SaveResult>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			Callable<SaveResult> task = () -> {
				ready.countDown();
				start.await();
				return service.save(MEMBER, request("777", "CE7"));
			};
			futures.add(executor.submit(task));
		}
		ready.await();
		start.countDown();
		List<SaveResult> results = new ArrayList<>();
		for (Future<SaveResult> future : futures) {
			results.add(future.get());
		}
		executor.shutdown();

		assertThat(results).extracting(result -> result.place().savedPlaceId()).containsOnly(
				results.get(0).place().savedPlaceId());
		assertThat(results).filteredOn(SaveResult::created).hasSize(1);
		assertThat(jdbc.queryForObject("select count(*) from saved_places where member_id = ?", Integer.class, MEMBER))
				.isEqualTo(1);
	}

	@Test
	@DisplayName("상세는 본인의 장소만 돌려주고 다른 회원의 장소와 없는 장소는 같은 404다")
	void detailHidesOtherMembersAndMissingPlaces() {
		long id = service.save(MEMBER, request("100", "CE7")).place().savedPlaceId();

		assertThat(service.detail(MEMBER, id).savedPlaceId()).isEqualTo(id);
		assertThatThrownBy(() -> service.detail(OTHER, id)).satisfies(thrown -> assertThat(errorCode(thrown)).isEqualTo("PLACE-0404"));
		assertThatThrownBy(() -> service.detail(MEMBER, id + 1_000_000)).satisfies(thrown -> assertThat(errorCode(thrown)).isEqualTo("PLACE-0404"));
	}

	@Test
	@DisplayName("삭제하면 사라지고 다시 삭제하면 404다")
	void deleteRemovesPlace() {
		long id = service.save(MEMBER, request("100", "CE7")).place().savedPlaceId();

		service.delete(MEMBER, id);

		assertThatThrownBy(() -> service.detail(MEMBER, id)).satisfies(thrown -> assertThat(errorCode(thrown)).isEqualTo("PLACE-0404"));
		assertThatThrownBy(() -> service.delete(MEMBER, id)).satisfies(thrown -> assertThat(errorCode(thrown)).isEqualTo("PLACE-0404"));
	}

	@Test
	@DisplayName("다른 회원의 장소는 삭제할 수 없고 그대로 남는다")
	void cannotDeleteOtherMembersPlace() {
		long id = service.save(MEMBER, request("100", "CE7")).place().savedPlaceId();

		assertThatThrownBy(() -> service.delete(OTHER, id)).satisfies(thrown -> assertThat(errorCode(thrown)).isEqualTo("PLACE-0404"));

		assertThat(service.detail(MEMBER, id).savedPlaceId()).isEqualTo(id);
	}

	@Test
	@DisplayName("삭제한 장소는 다시 저장할 수 있다(하트를 껌다 켜는 경우)")
	void canSaveAgainAfterDelete() {
		long first = service.save(MEMBER, request("100", "CE7")).place().savedPlaceId();
		service.delete(MEMBER, first);

		SaveResult again = service.save(MEMBER, request("100", "CE7"));

		assertThat(again.created()).isTrue();
		assertThat(again.place().savedPlaceId()).isNotEqualTo(first);
	}

	@Test
	@DisplayName("목록은 커서로 이어 읽고 마지막 쪽에서 hasNext가 false다")
	void listsAcrossPagesWithCursor() {
		List<Long> ids = new ArrayList<>();
		for (int i = 1; i <= 5; i++) {
			long id = service.save(MEMBER, request(String.valueOf(i), "CE7")).place().savedPlaceId();
			age(id, "2026-11-0" + i + "T00:00:00Z");
			ids.add(id);
		}

		SavedPlaceListResponse first = service.list(MEMBER, null, 2, null);
		SavedPlaceListResponse second = service.list(MEMBER, first.nextCursor(), 2, null);
		SavedPlaceListResponse third = service.list(MEMBER, second.nextCursor(), 2, null);

		assertThat(first.items()).extracting(SavedPlaceResponse::savedPlaceId).containsExactly(ids.get(4), ids.get(3));
		assertThat(first.hasNext()).isTrue();
		assertThat(second.items()).extracting(SavedPlaceResponse::savedPlaceId).containsExactly(ids.get(2), ids.get(1));
		assertThat(second.hasNext()).isTrue();
		assertThat(third.items()).extracting(SavedPlaceResponse::savedPlaceId).containsExactly(ids.get(0));
		assertThat(third.hasNext()).isFalse();
		assertThat(third.nextCursor()).isNull();
	}

	@Test
	@DisplayName("정확히 size개만 있으면 hasNext는 false다")
	void hasNextIsFalseWhenExactlyPageSize() {
		service.save(MEMBER, request("1", "CE7"));
		service.save(MEMBER, request("2", "CE7"));

		SavedPlaceListResponse page = service.list(MEMBER, null, 2, null);

		assertThat(page.items()).hasSize(2);
		assertThat(page.hasNext()).isFalse();
		assertThat(page.nextCursor()).isNull();
	}

	@Test
	@DisplayName("목록은 본인의 장소만 보여 주고 분류로 거를 수 있다")
	void listsOnlyOwnPlacesAndFiltersByCategory() {
		long cafe = service.save(MEMBER, request("1", "CE7")).place().savedPlaceId();
		service.save(MEMBER, request("2", "FD6"));
		service.save(OTHER, request("3", "CE7"));

		assertThat(service.list(MEMBER, null, 20, null).items()).hasSize(2);
		assertThat(service.list(MEMBER, null, 20, "CE7").items()).extracting(SavedPlaceResponse::savedPlaceId)
				.containsExactly(cafe);
		assertThat(service.list(MEMBER, null, 20, " ").items()).hasSize(2);
		assertThat(service.list(OTHER, null, 20, null).items()).hasSize(1);
	}

	@Test
	@DisplayName("저장한 장소가 없으면 빈 목록이다")
	void listsEmptyWhenNothingSaved() {
		SavedPlaceListResponse page = service.list(MEMBER, null, 20, null);

		assertThat(page.items()).isEmpty();
		assertThat(page.hasNext()).isFalse();
		assertThat(page.nextCursor()).isNull();
	}

	@Test
	@DisplayName("잘못되었거나 다른 회원·다른 필터의 커서는 400이다")
	void rejectsInvalidOrForeignCursor() {
		for (int i = 1; i <= 3; i++) {
			service.save(MEMBER, request(String.valueOf(i), "CE7"));
		}
		String cursor = service.list(MEMBER, null, 1, null).nextCursor();

		assertThat(cursor).isNotNull();
		assertThatThrownBy(() -> service.list(MEMBER, "not-a-cursor", 1, null))
				.satisfies(thrown -> assertThat(errorCode(thrown)).isEqualTo("COMMON-0400"));
		assertThatThrownBy(() -> service.list(OTHER, cursor, 1, null))
				.satisfies(thrown -> assertThat(errorCode(thrown)).isEqualTo("COMMON-0400"));
		assertThatThrownBy(() -> service.list(MEMBER, cursor, 1, "CE7"))
				.satisfies(thrown -> assertThat(errorCode(thrown)).isEqualTo("COMMON-0400"));
		assertThatThrownBy(() -> service.list(MEMBER, "x".repeat(300), 1, null))
				.satisfies(thrown -> assertThat(errorCode(thrown)).isEqualTo("COMMON-0400"));
	}
}
