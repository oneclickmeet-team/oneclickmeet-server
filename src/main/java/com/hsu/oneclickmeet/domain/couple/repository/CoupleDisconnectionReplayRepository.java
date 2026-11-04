package com.hsu.oneclickmeet.domain.couple.repository;

import com.hsu.oneclickmeet.domain.couple.entity.CoupleDisconnectionReplay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoupleDisconnectionReplayRepository extends JpaRepository<CoupleDisconnectionReplay, Long> {

	boolean existsByMemberIdAndCoupleIdAndRequestKey(long memberId, long coupleId, String requestKey);

	@Modifying
	@Query(value = """
		INSERT INTO couple_disconnection_replays (member_id, couple_id, request_key)
		VALUES (:memberId, :coupleId, :requestKey)
		ON DUPLICATE KEY UPDATE id = id
		""", nativeQuery = true)
	void prepare(@Param("memberId") long memberId, @Param("coupleId") long coupleId,
		@Param("requestKey") String requestKey);

	@Query(value = """
		SELECT * FROM couple_disconnection_replays
		WHERE member_id = :memberId AND couple_id = :coupleId AND request_key = :requestKey FOR UPDATE NOWAIT
		""", nativeQuery = true)
	CoupleDisconnectionReplay lock(@Param("memberId") long memberId, @Param("coupleId") long coupleId,
		@Param("requestKey") String requestKey);
}
