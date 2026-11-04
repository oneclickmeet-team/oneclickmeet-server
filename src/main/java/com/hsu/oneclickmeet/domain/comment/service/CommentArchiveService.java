package com.hsu.oneclickmeet.domain.comment.service;

import com.hsu.oneclickmeet.domain.comment.entity.CommentTargetType;
import com.hsu.oneclickmeet.domain.comment.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.stream.LongStream;

@Service
@RequiredArgsConstructor
public class CommentArchiveService {

	private final CommentRepository comments;

	/**
	 * The caller holds the source couple lock in a READ_COMMITTED transaction and supplies
	 * the two members' fresh copy IDs, or the same IDs on retry.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void copyForUnshare(long sharedCourseId, long firstCourseId, long secondCourseId, Instant archivedAt) {
		if (firstCourseId == secondCourseId) {
			throw new IllegalArgumentException("두 사용자에게 각각 다른 개인 사본 ID가 필요합니다.");
		}
		// Each fresh copy receives all archived comments in this transaction, so any row marks a completed copy.
		var pendingCopyIds = LongStream.of(firstCourseId, secondCourseId)
				.filter(courseId -> !comments.existsByTargetTypeAndTargetId(CommentTargetType.COURSE, courseId))
				.toArray();
		if (pendingCopyIds.length == 0) {
			return;
		}
		var sharedComments = comments.findAllByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(
				CommentTargetType.SHARED_COURSE, sharedCourseId);
		for (var comment : sharedComments) {
			for (long courseId : pendingCopyIds) {
				comments.save(comment.archiveForCourse(courseId, archivedAt));
			}
		}
	}
}
