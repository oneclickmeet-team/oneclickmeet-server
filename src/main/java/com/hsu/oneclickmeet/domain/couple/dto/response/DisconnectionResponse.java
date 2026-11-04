package com.hsu.oneclickmeet.domain.couple.dto.response;

import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;

import java.time.Instant;

public record DisconnectionResponse(long coupleId, CoupleStatus status, Instant disconnectedAt, Instant graceEndsAt) {
}
