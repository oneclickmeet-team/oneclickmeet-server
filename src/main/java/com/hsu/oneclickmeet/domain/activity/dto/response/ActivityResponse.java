package com.hsu.oneclickmeet.domain.activity.dto.response;

import com.hsu.oneclickmeet.domain.activity.entity.Activity;
import com.hsu.oneclickmeet.domain.activity.entity.ActivityType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record ActivityResponse(
		long activityId,
		ActivityType type,
		String targetType,
		long targetId,
		String summary,
		Instant createdAt) {

	public static ActivityResponse from(Activity activity) {
		return new ActivityResponse(activity.getId(), activity.getType(), activity.getTargetType(),
				activity.getTargetId(), activity.getSummary(),
				activity.getCreatedAt() == null ? null : activity.getCreatedAt().truncatedTo(ChronoUnit.MICROS));
	}
}
