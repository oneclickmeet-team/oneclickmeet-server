package com.hsu.oneclickmeet.domain.course.dto.response;

import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;

import java.time.Instant;
import java.util.List;

public record SharedCourseListResponse(long coupleId, CoupleStatus relationshipStatus, boolean readOnly,
		Instant graceEndsAt, List<Item> items, String nextCursor, boolean hasNext) {

	public record Item(long sharedCourseId, String courseName, int placeCount, Instant sharedAt) {
	}
}
