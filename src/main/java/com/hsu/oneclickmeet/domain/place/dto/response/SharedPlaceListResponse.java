package com.hsu.oneclickmeet.domain.place.dto.response;

import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;

import java.time.Instant;
import java.util.List;

public record SharedPlaceListResponse(
		long coupleId,
		CoupleStatus relationshipStatus,
		boolean readOnly,
		Instant graceEndsAt,
		List<SharedPlaceResponse> items,
		String nextCursor,
		boolean hasNext) {
}
