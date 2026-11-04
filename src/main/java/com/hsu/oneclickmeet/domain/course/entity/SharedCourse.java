package com.hsu.oneclickmeet.domain.course.entity;

import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@Table(name = "shared_course", uniqueConstraints = @UniqueConstraint(
		name = "uk_shared_course_couple_source", columnNames = {"couple_id", "source_course_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SharedCourse extends BaseEntity {

	@Id
	@GeneratedValue
	@Column(name = "shared_course_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "course_id", nullable = false)
	private Course course;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "couple_id", nullable = false)
	private Couple couple;

	// The original may be deleted independently of the shared snapshot.
	@Column(name = "source_course_id", nullable = false)
	private long sourceCourseId;

	@Column(name = "shared_by_member_id", nullable = false)
	private long sharedByMemberId;

	@Column(name = "structure_version", nullable = false)
	private long structureVersion;

	@Column(name = "last_edited_by_member_id")
	private Long lastEditedByMemberId;

	@Column(name = "last_edited_at")
	private Instant lastEditedAt;

	private SharedCourse(Course course, Couple couple, long sourceCourseId, long sharedByMemberId) {
		this.course = course;
		this.couple = couple;
		this.sourceCourseId = sourceCourseId;
		this.sharedByMemberId = sharedByMemberId;
		this.structureVersion = 1;
	}

	public static SharedCourse create(Course snapshot, Couple couple, long sourceCourseId, long sharedByMemberId) {
		return new SharedCourse(snapshot, couple, sourceCourseId, sharedByMemberId);
	}

	public void recordEdit(long memberId, Instant editedAt, boolean structureChanged) {
		this.lastEditedByMemberId = memberId;
		this.lastEditedAt = editedAt;
		if (structureChanged) {
			structureVersion++;
		}
	}
}
