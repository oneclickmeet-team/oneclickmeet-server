package com.hsu.oneclickmeet.domain.comment.entity;

import jakarta.persistence.CheckConstraint;
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

@Entity
@Getter
@Table(name = "comments", indexes = @Index(name = "idx_comments_target_created",
		columnList = "target_type, target_id, created_at, id"),
		check = @CheckConstraint(name = "chk_comments_archived_target",
				constraint = "(target_type = 'COURSE' AND archived_at IS NOT NULL)"
						+ " OR (target_type = 'SHARED_COURSE' AND archived_at IS NULL)"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private CommentTargetType targetType;

	@Column(nullable = false, updatable = false)
	private long targetId;

	@Column(nullable = false, updatable = false)
	private long authorMemberId;

	@Column(nullable = false, length = 1000)
	private String content;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@Column(updatable = false)
	private Instant archivedAt;

	public Comment(long sharedCourseId, long authorMemberId, String content, Instant now) {
		this.targetType = CommentTargetType.SHARED_COURSE;
		this.targetId = sharedCourseId;
		this.authorMemberId = authorMemberId;
		this.content = content;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void changeContent(String content, Instant now) {
		this.content = content;
		this.updatedAt = now;
	}

	public Comment archiveForCourse(long courseId, Instant archivedAt) {
		Comment archived = new Comment();
		archived.targetType = CommentTargetType.COURSE;
		archived.targetId = courseId;
		archived.authorMemberId = authorMemberId;
		archived.content = content;
		archived.createdAt = createdAt;
		archived.updatedAt = updatedAt;
		archived.archivedAt = archivedAt;
		return archived;
	}
}
