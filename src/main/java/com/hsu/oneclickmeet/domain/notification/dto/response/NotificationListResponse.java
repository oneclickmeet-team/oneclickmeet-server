package com.hsu.oneclickmeet.domain.notification.dto.response;

import java.util.List;

public record NotificationListResponse(List<NotificationResponse> items, long unreadCount, String nextCursor,
		boolean hasNext) {
}
