package com.hsu.oneclickmeet.domain.couple.repository;

import com.hsu.oneclickmeet.domain.couple.entity.CoupleReconnectionReplay;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoupleReconnectionReplayRepository extends JpaRepository<CoupleReconnectionReplay, Long> {

	Optional<CoupleReconnectionReplay> findByMemberIdAndCoupleIdAndRequestKey(
		long memberId, long coupleId, String requestKey);
}
