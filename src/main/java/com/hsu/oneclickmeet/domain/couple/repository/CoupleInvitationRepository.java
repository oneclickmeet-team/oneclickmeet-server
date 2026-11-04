package com.hsu.oneclickmeet.domain.couple.repository;

import com.hsu.oneclickmeet.domain.couple.entity.CoupleInvitation;
import com.hsu.oneclickmeet.domain.couple.entity.InvitationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface CoupleInvitationRepository extends JpaRepository<CoupleInvitation, Long> {

	Optional<CoupleInvitation> findByCode(String code);

	boolean existsByCode(String code);

	@Query("select i.issuerId from CoupleInvitation i where i.code = :code")
	Optional<Long> findIssuerIdByCode(@Param("code") String code);

	@Query("""
		select i from CoupleInvitation i
		where i.issuerId = :issuerId and i.purpose = :purpose
		  and i.consumed = false and i.revoked = false
		""")
	Optional<CoupleInvitation> findPending(@Param("issuerId") long issuerId,
		@Param("purpose") InvitationPurpose purpose);

	default Optional<CoupleInvitation> findActive(long issuerId, InvitationPurpose purpose, Instant now) {
		return findPending(issuerId, purpose).filter(invitation -> invitation.isAvailable(now));
	}
}
