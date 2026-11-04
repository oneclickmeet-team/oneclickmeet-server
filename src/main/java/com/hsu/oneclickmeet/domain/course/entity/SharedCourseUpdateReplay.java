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
@Table(name = "shared_course_update_replays", uniqueConstraints = @UniqueConstraint(
		name = "uk_shared_course_update_replays_member_course_key",
		columnNames = {"member_id", "shared_course_id", "request_key"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SharedCourseUpdateReplay {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private long memberId;

	@Column(name = "shared_course_id", nullable = false)
	private long sharedCourseId;

	@Column(name = "request_key", nullable = false, length = 16)
	private UUID requestKey;

	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Column(name = "structure_version", nullable = false)
	private long structureVersion;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	public SharedCourseUpdateReplay(long memberId, long sharedCourseId, UUID requestKey) {
		this.memberId = memberId;
		this.sharedCourseId = sharedCourseId;
		this.requestKey = requestKey;
	}

	public boolean isReplayable(Instant now) {
		return now.isBefore(expiresAt);
	}

	public void complete(String requestHash, long structureVersion, Instant updatedAt, Instant expiresAt) {
		this.requestHash = requestHash;
		this.structureVersion = structureVersion;
		this.updatedAt = updatedAt;
		this.expiresAt = expiresAt;
	}
}
