package com.hsu.oneclickmeet.domain.activity.entity;

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

/** 회원이 한 활동(공유·삭제 등)의 기록. 기록은 만든 뒤 바꾸지 않는다. */
@Entity
@Getter
@Table(name = "activities", indexes = @Index(name = "idx_activities_member_id", columnList = "member_id, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Activity extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private long memberId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 30)
	private ActivityType type;

	@Column(nullable = false, updatable = false, length = 30)
	private String targetType;

	@Column(nullable = false, updatable = false)
	private long targetId;

	@Column(nullable = false, updatable = false, length = 200)
	private String summary;

	private Activity(long memberId, ActivityType type, String targetType, long targetId, String summary) {
		this.memberId = memberId;
		this.type = type;
		this.targetType = targetType;
		this.targetId = targetId;
		this.summary = summary;
	}

	public static Activity create(long memberId, ActivityType type, String targetType, long targetId, String summary) {
		if (memberId <= 0) {
			throw new IllegalArgumentException("memberId는 양수여야 합니다.");
		}
		if (type == null) {
			throw new IllegalArgumentException("type은 필수입니다.");
		}
		if (targetType == null || targetType.isBlank() || targetType.trim().length() > 30) {
			throw new IllegalArgumentException("targetType은 1~30자여야 합니다.");
		}
		if (targetId <= 0) {
			throw new IllegalArgumentException("targetId는 양수여야 합니다.");
		}
		if (summary == null || summary.isBlank() || summary.trim().length() > 200) {
			throw new IllegalArgumentException("summary는 1~200자여야 합니다.");
		}
		return new Activity(memberId, type, targetType.trim(), targetId, summary.trim());
	}
}
