package com.hsu.oneclickmeet.domain.couple.dto.response;

import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;

public record ConnectionResponse(long coupleId, CoupleStatus status) {
}
