package com.hsu.oneclickmeet.domain.notification.entity;

import com.hsu.oneclickmeet.domain.activity.entity.ActivityType;
import com.hsu.oneclickmeet.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 회원에게 보여 줄 알림. 읽으면 읽은 시각이 기록된다. */
@Entity
@Getter
@Table(name = "notifications", indexes = @Index(name = "idx_notifications_member_id", columnList = "member_id, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private long memberId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 30)
	private ActivityType type;

	@Column(nullable = false, updatable = false, length = 100)
	private String title;

	@Column(nullable = false, updatable = false, length = 300)
	private String body;

	@Column(nullable = false, updatable = false, length = 30)
	private String targetType;

	@Column(nullable = false, updatable = false)
	private long targetId;

	private Instant readAt;

	private Notification(long memberId, ActivityType type, String title, String body, String targetType,
			long targetId) {
		this.memberId = memberId;
		this.type = type;
		this.title = title;
		this.body = body;
		this.targetType = targetType;
		this.targetId = targetId;
	}

	public static Notification create(long memberId, ActivityType type, String title, String body, String targetType,
			long targetId) {
		if (memberId <= 0) {
			throw new IllegalArgumentException("memberId는 양수여야 합니다.");
		}
		if (type == null) {
			throw new IllegalArgumentException("type은 필수입니다.");
		}
		if (title == null || title.isBlank() || title.trim().length() > 100) {
			throw new IllegalArgumentException("title은 1~100자여야 합니다.");
		}
		if (body == null || body.isBlank() || body.trim().length() > 300) {
			throw new IllegalArgumentException("body는 1~300자여야 합니다.");
		}
		if (targetType == null || targetType.isBlank() || targetType.trim().length() > 30) {
			throw new IllegalArgumentException("targetType은 1~30자여야 합니다.");
		}
		if (targetId <= 0) {
			throw new IllegalArgumentException("targetId는 양수여야 합니다.");
		}
		return new Notification(memberId, type, title.trim(), body.trim(), targetType.trim(), targetId);
	}

	public boolean isRead() {
		return readAt != null;
	}
}
