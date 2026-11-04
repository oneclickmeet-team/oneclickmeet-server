package com.hsu.oneclickmeet.domain.couple.repository;

import com.hsu.oneclickmeet.domain.couple.entity.CoupleDisconnectionReplay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CoupleDisconnectionReplayRepository extends JpaRepository<CoupleDisconnectionReplay, Long> {

	Optional<CoupleDisconnectionReplay> findByMemberIdAndCoupleIdAndRequestKey(
		long memberId, long coupleId, String requestKey);
}
