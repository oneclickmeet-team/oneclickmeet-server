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

import java.util.UUID;

@Entity
@Getter
@Table(name = "course_save_requests", uniqueConstraints = @UniqueConstraint(
		name = "uk_course_save_requests_member_key", columnNames = {"member_id", "request_key"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseSaveRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private long memberId;

	@Column(name = "request_key", nullable = false, length = 16)
	private UUID requestKey;

	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Column(name = "course_id", nullable = false)
	private long courseId;

	public CourseSaveRequest(long memberId, UUID requestKey, String requestHash, long courseId) {
		this.memberId = memberId;
		this.requestKey = requestKey;
		this.requestHash = requestHash;
		this.courseId = courseId;
	}
}
