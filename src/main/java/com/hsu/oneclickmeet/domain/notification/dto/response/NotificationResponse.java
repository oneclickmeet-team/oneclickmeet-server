package com.hsu.oneclickmeet.domain.notification.dto.response;

import com.hsu.oneclickmeet.domain.activity.entity.ActivityType;
import com.hsu.oneclickmeet.domain.notification.entity.Notification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record NotificationResponse(
		long notificationId,
		ActivityType type,
		String title,
		String body,
		String targetType,
		long targetId,
		boolean read,
		Instant createdAt) {

	public static NotificationResponse from(Notification notification) {
		return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
				notification.getBody(), notification.getTargetType(), notification.getTargetId(),
				notification.isRead(),
				notification.getCreatedAt() == null ? null
						: notification.getCreatedAt().truncatedTo(ChronoUnit.MICROS));
	}
}
