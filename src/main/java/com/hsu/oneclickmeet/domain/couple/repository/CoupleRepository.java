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

	String CURRENT_COUPLE = """
		select c from Couple c
		where (c.firstMemberId = :memberId or c.secondMemberId = :memberId)
		  and (c.status = com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus.CONNECTED
		    or (c.status = com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus.GRACE and c.graceEndsAt > :now))
		""";

	@Query(CURRENT_COUPLE)
	Optional<Couple> findCurrent(@Param("memberId") long memberId, @Param("now") Instant now);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query(CURRENT_COUPLE)
	Optional<Couple> findCurrentForUpdate(@Param("memberId") long memberId, @Param("now") Instant now);
}
