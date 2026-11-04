package com.hsu.oneclickmeet.domain.couple.repository;

import com.hsu.oneclickmeet.domain.couple.entity.CoupleConnectionReplay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoupleConnectionReplayRepository extends JpaRepository<CoupleConnectionReplay, Long> {

	boolean existsByMemberIdAndRequestKey(long memberId, String requestKey);

	@Modifying
	@Query(value = """
			INSERT INTO couple_connection_replays (member_id, request_key) VALUES (:memberId, :requestKey)
			ON DUPLICATE KEY UPDATE id = id
			""", nativeQuery = true)
	void prepare(@Param("memberId") long memberId, @Param("requestKey") String requestKey);

	@Query(value = """
			SELECT * FROM couple_connection_replays
			WHERE member_id = :memberId AND request_key = :requestKey FOR UPDATE NOWAIT
			""", nativeQuery = true)
	CoupleConnectionReplay lock(@Param("memberId") long memberId, @Param("requestKey") String requestKey);
}
