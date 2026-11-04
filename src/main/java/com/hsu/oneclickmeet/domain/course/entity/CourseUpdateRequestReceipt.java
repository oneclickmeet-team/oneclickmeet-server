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
@Table(name = "course_update_requests", uniqueConstraints = @UniqueConstraint(
		name = "uk_course_update_requests_scope", columnNames = {"member_id", "course_id", "request_key"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseUpdateRequestReceipt {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private long memberId;

	@Column(name = "course_id", nullable = false)
	private long courseId;

	@Column(name = "request_key", nullable = false, length = 16)
	private UUID requestKey;

	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public CourseUpdateRequestReceipt(long memberId, long courseId, UUID requestKey, String requestHash,
			Instant updatedAt) {
		this.memberId = memberId;
		this.courseId = courseId;
		this.requestKey = requestKey;
		this.requestHash = requestHash;
		this.updatedAt = updatedAt;
	}
}
