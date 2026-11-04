package com.hsu.oneclickmeet.domain.couple.repository;

import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface CoupleRepository extends JpaRepository<Couple, Long> {

	@Query("""
		select c from Couple c
		where (c.firstMemberId = :memberId or c.secondMemberId = :memberId)
		  and (c.status = com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus.CONNECTED
		    or (c.status = com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus.GRACE and c.graceEndsAt > :now))
		""")
	Optional<Couple> findCurrent(@Param("memberId") long memberId, @Param("now") Instant now);

	@Query("""
		select c.id from Couple c
		where c.id = :coupleId and (c.firstMemberId = :memberId or c.secondMemberId = :memberId)
		""")
	Optional<Long> findMemberCoupleId(@Param("memberId") long memberId, @Param("coupleId") long coupleId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select c from Couple c
		where c.id = :coupleId and (c.firstMemberId = :memberId or c.secondMemberId = :memberId)
		""")
	Optional<Couple> findMemberCoupleForUpdate(@Param("memberId") long memberId, @Param("coupleId") long coupleId);
}
