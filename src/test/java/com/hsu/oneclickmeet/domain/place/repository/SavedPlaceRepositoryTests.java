package com.hsu.oneclickmeet.domain.place.repository;

import com.hsu.oneclickmeet.domain.place.entity.PlaceProvider;
import com.hsu.oneclickmeet.domain.place.entity.SavedPlace;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class SavedPlaceRepositoryTests {

	private static final long MEMBER = 810_001L;
	private static final long OTHER = 810_002L;

	@Autowired
	private SavedPlaceRepository repository;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private TransactionTemplate transaction;
	@PersistenceContext
	private EntityManager entityManager;

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from saved_places where member_id in (?, ?)", MEMBER, OTHER);
	}

	private SavedPlace place(long memberId, String providerPlaceId, String groupCode) {
		return SavedPlace.create(memberId, PlaceProvider.KAKAO, providerPlaceId, "장소 " + providerPlaceId, "주소",
				"도로명", "음식점 > 카페", groupCode, 37.5822, 127.0105, "http://place.map.kakao.com/" + providerPlaceId);
	}

	private long insertAt(long memberId, String providerPlaceId, String groupCode, String createdAt) {
		SavedPlace saved = repository.saveAndFlush(place(memberId, providerPlaceId, groupCode));
		jdbc.update("update saved_places set created_at = ? where id = ?",
				Timestamp.from(Instant.parse(createdAt)), saved.getId());
		return saved.getId();
	}

	@Test
	@DisplayName("저장하고 다시 읽어도 값이 유지된다")
	void savesAndReloadsValues() {
		long id = repository.saveAndFlush(place(MEMBER, "100", "CE7")).getId();
		entityManager.clear();

		SavedPlace reloaded = repository.findById(id).orElseThrow();

		assertThat(reloaded.getMemberId()).isEqualTo(MEMBER);
		assertThat(reloaded.getProvider()).isEqualTo(PlaceProvider.KAKAO);
		assertThat(reloaded.getProviderPlaceId()).isEqualTo("100");
		assertThat(reloaded.getName()).isEqualTo("장소 100");
		assertThat(reloaded.getRoadAddress()).isEqualTo("도로명");
		assertThat(reloaded.getCategoryGroupCode()).isEqualTo("CE7");
		assertThat(reloaded.getLat()).isEqualTo(37.5822);
		assertThat(reloaded.getLng()).isEqualTo(127.0105);
		assertThat(reloaded.getCreatedAt()).isNotNull();
		assertThat(reloaded.getUpdatedAt()).isNotNull();
	}

	@Test
	@DisplayName("같은 회원이 같은 장소를 두 번 저장하면 DB가 거절한다")
	void rejectsDuplicateForSameMember() {
		repository.saveAndFlush(place(MEMBER, "100", "CE7"));

		assertThatThrownBy(() -> repository.saveAndFlush(place(MEMBER, "100", "CE7")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("다른 회원은 같은 장소를 각자 저장할 수 있다")
	void allowsSamePlaceForDifferentMembers() {
		repository.saveAndFlush(place(MEMBER, "100", "CE7"));
		repository.saveAndFlush(place(OTHER, "100", "CE7"));

		assertThat(repository.findByMemberIdAndProviderAndProviderPlaceId(MEMBER, PlaceProvider.KAKAO, "100")).isPresent();
		assertThat(repository.findByMemberIdAndProviderAndProviderPlaceId(OTHER, PlaceProvider.KAKAO, "100")).isPresent();
	}

	@Test
	@DisplayName("회원 번호로 조회하면 다른 회원의 장소는 찾지 못한다")
	void doesNotFindOtherMembersPlace() {
		long id = repository.saveAndFlush(place(MEMBER, "100", "CE7")).getId();

		assertThat(repository.findByIdAndMemberId(id, MEMBER)).isPresent();
		assertThat(repository.findByIdAndMemberId(id, OTHER)).isEmpty();
		assertThat(repository.existsByIdAndMemberId(id, MEMBER)).isTrue();
		assertThat(repository.existsByIdAndMemberId(id, OTHER)).isFalse();
	}

	@Test
	@DisplayName("목록은 저장 시각 내림차순이고 같은 시각이면 id 내림차순이다")
	void ordersByCreatedAtThenIdDescending() {
		long oldest = insertAt(MEMBER, "1", "CE7", "2026-11-01T00:00:00Z");
		long sameTimeFirst = insertAt(MEMBER, "2", "FD6", "2026-11-02T00:00:00Z");
		long sameTimeSecond = insertAt(MEMBER, "3", "FD6", "2026-11-02T00:00:00Z");
		insertAt(OTHER, "4", "CE7", "2026-11-03T00:00:00Z");

		List<SavedPlace> page = repository.findPage(MEMBER, null, null, null, PageRequest.ofSize(10));

		assertThat(page).extracting(SavedPlace::getId).containsExactly(sameTimeSecond, sameTimeFirst, oldest);
	}

	@Test
	@DisplayName("커서 다음 위치부터 읽는다")
	void readsAfterCursor() {
		long oldest = insertAt(MEMBER, "1", "CE7", "2026-11-01T00:00:00Z");
		long middle = insertAt(MEMBER, "2", "CE7", "2026-11-02T00:00:00Z");
		long newest = insertAt(MEMBER, "3", "CE7", "2026-11-03T00:00:00Z");

		List<SavedPlace> afterNewest = repository.findPage(MEMBER, null, Instant.parse("2026-11-03T00:00:00Z"),
				newest, PageRequest.ofSize(10));
		List<SavedPlace> afterMiddle = repository.findPage(MEMBER, null, Instant.parse("2026-11-02T00:00:00Z"),
				middle, PageRequest.ofSize(10));

		assertThat(afterNewest).extracting(SavedPlace::getId).containsExactly(middle, oldest);
		assertThat(afterMiddle).extracting(SavedPlace::getId).containsExactly(oldest);
	}

	@Test
	@DisplayName("같은 시각의 항목은 커서 id보다 작은 것만 이어서 읽는다")
	void readsOnlySmallerIdsAtSameTimestampAfterCursor() {
		long first = insertAt(MEMBER, "1", "CE7", "2026-11-02T00:00:00Z");
		long second = insertAt(MEMBER, "2", "CE7", "2026-11-02T00:00:00Z");
		long third = insertAt(MEMBER, "3", "CE7", "2026-11-02T00:00:00Z");

		List<SavedPlace> page = repository.findPage(MEMBER, null, Instant.parse("2026-11-02T00:00:00Z"), second,
				PageRequest.ofSize(10));

		assertThat(page).extracting(SavedPlace::getId).containsExactly(first);
		assertThat(third).isGreaterThan(second);
	}

	@Test
	@DisplayName("분류(categoryGroupCode)로 거를 수 있다")
	void filtersByCategoryGroupCode() {
		long cafe = insertAt(MEMBER, "1", "CE7", "2026-11-01T00:00:00Z");
		insertAt(MEMBER, "2", "FD6", "2026-11-02T00:00:00Z");

		List<SavedPlace> cafes = repository.findPage(MEMBER, "CE7", null, null, PageRequest.ofSize(10));

		assertThat(cafes).extracting(SavedPlace::getId).containsExactly(cafe);
	}

	@Test
	@DisplayName("요청한 개수만큼만 읽는다")
	void limitsPageSize() {
		insertAt(MEMBER, "1", "CE7", "2026-11-01T00:00:00Z");
		insertAt(MEMBER, "2", "CE7", "2026-11-02T00:00:00Z");
		insertAt(MEMBER, "3", "CE7", "2026-11-03T00:00:00Z");

		assertThat(repository.findPage(MEMBER, null, null, null, PageRequest.ofSize(2))).hasSize(2);
	}
}
