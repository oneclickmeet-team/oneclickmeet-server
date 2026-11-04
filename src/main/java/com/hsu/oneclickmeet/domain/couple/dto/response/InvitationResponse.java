package com.hsu.oneclickmeet.domain.couple.dto.response;

import com.hsu.oneclickmeet.domain.couple.entity.InvitationPurpose;

import java.time.Instant;

public record InvitationResponse(long invitationId, InvitationPurpose purpose, String invitationCode, Long coupleId,
		Instant expiresAt) {
}
