package com.hsu.oneclickmeet.domain.couple.dto.response;

import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;

import java.time.Instant;

public record CoupleResponse(long coupleId, CoupleStatus status, PartnerResponse partner, Instant connectedAt,
		Instant disconnectedAt, Instant graceEndsAt, Integer remainingGraceDays, boolean sharedReadOnly,
		boolean canReconnect) {
}
