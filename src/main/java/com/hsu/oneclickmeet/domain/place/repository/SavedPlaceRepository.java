package com.hsu.oneclickmeet.domain.place.repository;

import com.hsu.oneclickmeet.domain.place.entity.PlaceProvider;
import com.hsu.oneclickmeet.domain.place.entity.SavedPlace;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SavedPlaceRepository extends JpaRepository<SavedPlace, Long> {

	Optional<SavedPlace> findByMemberIdAndProviderAndProviderPlaceId(
			long memberId, PlaceProvider provider, String providerPlaceId);

	Optional<SavedPlace> findByIdAndMemberId(long id, long memberId);

	boolean existsByIdAndMemberId(long id, long memberId);

	/** 코스 추천 후보로 쓸 최근 보관함 장소(최대 200개). */
	List<SavedPlace> findTop200ByMemberIdOrderByIdDesc(long memberId);

	/** 저장 시각 내림차순(같으면 id 내림차순)으로 한 쪽을 읽는다. 커서가 있으면 그 위치 다음부터 읽는다. */
	@Query("""
			select p from SavedPlace p
			where p.memberId = :memberId
			  and (:categoryGroupCode is null or p.categoryGroupCode = :categoryGroupCode)
			  and (:cursorCreatedAt is null
			       or p.createdAt < :cursorCreatedAt
			       or (p.createdAt = :cursorCreatedAt and p.id < :cursorId))
			order by p.createdAt desc, p.id desc
			""")
	List<SavedPlace> findPage(@Param("memberId") long memberId,
			@Param("categoryGroupCode") String categoryGroupCode,
			@Param("cursorCreatedAt") Instant cursorCreatedAt,
			@Param("cursorId") Long cursorId,
			Pageable pageable);
}
