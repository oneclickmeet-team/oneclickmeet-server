package com.hsu.oneclickmeet.domain.comment.service;

import com.hsu.oneclickmeet.domain.comment.entity.CommentTargetType;
import com.hsu.oneclickmeet.domain.comment.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CommentArchiveService {

	private final CommentRepository comments;

	@Transactional(propagation = Propagation.MANDATORY)
	public void copyForUnshare(long sharedCourseId, long firstCourseId, long secondCourseId, Instant archivedAt) {
		var sharedComments = comments.findAllByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(
				CommentTargetType.SHARED_COURSE, sharedCourseId);
		for (var comment : sharedComments) {
			comments.save(comment.archiveForCourse(firstCourseId, archivedAt));
			comments.save(comment.archiveForCourse(secondCourseId, archivedAt));
		}
	}
}
