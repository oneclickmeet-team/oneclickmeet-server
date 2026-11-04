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

	String CURRENT_STATUS = """
		(c.status = com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus.CONNECTED
		  or (c.status = com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus.GRACE and c.graceEndsAt > :now))
		""";
	String CURRENT_COUPLE = """
		select c from Couple c
		where (c.firstMemberId = :memberId or c.secondMemberId = :memberId) and
		""" + CURRENT_STATUS;

	@Query("""
		select count(c) > 0 from Couple c
		where c.id <> :coupleId
		  and (c.firstMemberId in (:memberId, :partnerId) or c.secondMemberId in (:memberId, :partnerId)) and
		""" + CURRENT_STATUS)
	boolean hasOtherCurrentCouple(@Param("coupleId") long coupleId, @Param("memberId") long memberId,
		@Param("partnerId") long partnerId, @Param("now") Instant now);

	@Query("""
		select case when c.firstMemberId = :memberId then c.secondMemberId else c.firstMemberId end from Couple c
		where c.id = :coupleId and (c.firstMemberId = :memberId or c.secondMemberId = :memberId)
		""")
	Optional<Long> findPartnerId(@Param("memberId") long memberId, @Param("coupleId") long coupleId);

	@Query(CURRENT_COUPLE)
	Optional<Couple> findCurrent(@Param("memberId") long memberId, @Param("now") Instant now);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query(CURRENT_COUPLE)
	Optional<Couple> findCurrentForUpdate(@Param("memberId") long memberId, @Param("now") Instant now);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select c from Couple c
		where c.id = :coupleId and (c.firstMemberId = :memberId or c.secondMemberId = :memberId)
		""")
	Optional<Couple> findMemberCoupleForUpdate(@Param("memberId") long memberId, @Param("coupleId") long coupleId);
}
