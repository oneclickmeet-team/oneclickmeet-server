package com.hsu.oneclickmeet.domain.member.repository;

import com.hsu.oneclickmeet.domain.member.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/** 아직 폐기되지 않은 경우에만 폐기한다. 처리된 행 수가 0이면 이미 폐기된(또는 없는) 토큰이다. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update RefreshToken t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
	int revoke(@Param("id") long id, @Param("now") Instant now);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update RefreshToken t set t.revokedAt = :now where t.memberId = :memberId and t.revokedAt is null")
	int revokeAllOf(@Param("memberId") long memberId, @Param("now") Instant now);
}
