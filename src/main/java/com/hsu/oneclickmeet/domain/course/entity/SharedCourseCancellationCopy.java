package com.hsu.oneclickmeet.domain.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shared_course_cancellation_copies", uniqueConstraints = @UniqueConstraint(
		name = "uk_shared_course_cancellation_member", columnNames = {"shared_course_id", "member_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SharedCourseCancellationCopy {

	// Retain the receipt even if the personal copy or the shared target is no longer accessible.
	@Id
	@Column(name = "course_id")
	private Long courseId;

	@Column(name = "shared_course_id", nullable = false)
	private long sharedCourseId;

	@Column(name = "member_id", nullable = false)
	private long memberId;

	public SharedCourseCancellationCopy(long courseId, long sharedCourseId, long memberId) {
		this.courseId = courseId;
		this.sharedCourseId = sharedCourseId;
		this.memberId = memberId;
	}
}
