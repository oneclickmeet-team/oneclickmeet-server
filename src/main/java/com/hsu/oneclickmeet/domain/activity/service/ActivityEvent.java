package com.hsu.oneclickmeet.domain.activity.service;

import com.hsu.oneclickmeet.domain.activity.entity.ActivityType;

/**
 * 다른 도메인(공유·삭제 등)이 활동을 알릴 때 넘기는 값.
 * recipientMemberId가 있고 행위자와 다르면 그 회원에게 알림도 만든다.
 */
public record ActivityEvent(
		long actorMemberId,
		Long recipientMemberId,
		ActivityType type,
		String targetType,
		long targetId,
		String summary) {
}
