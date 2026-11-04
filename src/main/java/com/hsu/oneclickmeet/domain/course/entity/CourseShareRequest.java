package com.hsu.oneclickmeet.domain.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "course_share_requests", uniqueConstraints = @UniqueConstraint(
		name = "uk_course_share_requests_member_source_key", columnNames = {"member_id", "source_course_id", "request_key"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseShareRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private long memberId;

	@Column(name = "source_course_id", nullable = false)
	private long sourceCourseId;

	@Column(name = "request_key", nullable = false, length = 16)
	private UUID requestKey;

	@Column(name = "shared_course_id", nullable = false)
	private long sharedCourseId;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	public CourseShareRequest(long memberId, long sourceCourseId, UUID requestKey) {
		this.memberId = memberId;
		this.sourceCourseId = sourceCourseId;
		this.requestKey = requestKey;
	}

	public boolean isReplayable(Instant now) {
		return now.isBefore(expiresAt);
	}

	public void complete(long sharedCourseId, Instant expiresAt) {
		this.sharedCourseId = sharedCourseId;
		this.expiresAt = expiresAt;
	}
}
