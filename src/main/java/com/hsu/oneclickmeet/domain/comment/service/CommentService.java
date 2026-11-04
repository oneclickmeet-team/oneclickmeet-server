package com.hsu.oneclickmeet.domain.comment.service;

import com.hsu.oneclickmeet.domain.comment.dto.response.CommentListResponse;
import com.hsu.oneclickmeet.domain.comment.dto.response.CommentResponse;
import com.hsu.oneclickmeet.domain.comment.entity.Comment;
import com.hsu.oneclickmeet.domain.comment.entity.CommentCreateRequestReplay;
import com.hsu.oneclickmeet.domain.comment.entity.CommentTargetType;
import com.hsu.oneclickmeet.domain.comment.exception.CommentErrorCode;
import com.hsu.oneclickmeet.domain.comment.repository.CommentCreateRequestRepository;
import com.hsu.oneclickmeet.domain.comment.repository.CommentRepository;
import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;
import com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleMemberRepository;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.course.exception.CourseErrorCode;
import com.hsu.oneclickmeet.domain.course.repository.CourseRepository;
import com.hsu.oneclickmeet.domain.course.repository.SharedCourseRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.ErrorCode;
import com.hsu.oneclickmeet.global.persistence.MySqlTransactionLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

@Service
public class CommentService {

	private static final Duration REPLAY_LIFETIME = Duration.ofHours(24);

	private final CommentRepository comments;
	private final CommentCreateRequestRepository replays;
	private final CourseRepository courses;
	private final SharedCourseRepository sharedCourses;
	private final CoupleRepository couples;
	private final CoupleMemberRepository members;
	private final MySqlTransactionLock requestLock;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	public CommentService(CommentRepository comments, CommentCreateRequestRepository replays,
			CourseRepository courses, SharedCourseRepository sharedCourses, CoupleRepository couples,
			CoupleMemberRepository members, MySqlTransactionLock requestLock, JsonMapper jsonMapper,
			@Qualifier("coupleClock") Clock clock) {
		this.comments = comments;
		this.replays = replays;
		this.courses = courses;
		this.sharedCourses = sharedCourses;
		this.couples = couples;
		this.members = members;
		this.requestLock = requestLock;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public CommentListResponse list(long memberId, CommentTargetType targetType, long targetId, String cursor, int size) {
		boolean readOnly = switch (targetType) {
			case COURSE -> {
				requireOwnedCourse(memberId, targetId, CourseErrorCode.NOT_FOUND);
				yield true;
			}
			case SHARED_COURSE -> {
				Couple couple = requireCurrentCouple(memberId, false, CourseErrorCode.NOT_FOUND);
				requireSharedCourse(targetId, couple, CourseErrorCode.NOT_FOUND);
				yield couple.getStatus() == CoupleStatus.GRACE;
			}
		};
		CommentCursor after = CommentCursor.decode(cursor, targetType, targetId, size);
		var page = comments.findPage(targetType, targetId, after == null ? null : after.createdAt(),
				after == null ? null : after.commentId(), PageRequest.of(0, size + 1));
		var authorNames = new HashMap<Long, Optional<String>>();
		var items = page.stream().limit(size).map(comment -> {
			var name = authorNames.computeIfAbsent(comment.getAuthorMemberId(), members::findDisplayName);
			return toResponse(comment, memberId, name.orElse(null), readOnly);
		}).toList();
		boolean hasNext = page.size() > size;
		String nextCursor = null;
		if (hasNext) {
			var last = items.getLast();
			nextCursor = new CommentCursor(last.createdAt(), last.commentId()).encode(targetType, targetId, size);
		}
		return new CommentListResponse(items, nextCursor, hasNext);
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public CommentResponse create(long memberId, long sharedCourseId, String content, UUID requestKey) {
		boolean acquired = requestLock.tryAcquire(":comment-create:" + memberId + ":" + requestKey);
		Couple couple = requireCurrentCouple(memberId, acquired, CoupleErrorCode.STATE_CONFLICT);
		requireSharedCourse(sharedCourseId, couple, CourseErrorCode.NOT_FOUND);
		requireConnected(couple);
		if (!acquired) {
			throw new BusinessException(CommentErrorCode.REQUEST_IN_PROGRESS);
		}

		var previous = replays.findByMemberIdAndRequestKey(memberId, requestKey);
		if (previous.isPresent() && previous.get().isReplayable(now())) {
			var response = jsonMapper.readValue(previous.get().getResponseJson(), CommentResponse.class);
			if (response.targetId() != sharedCourseId || !response.content().equals(content)) {
				throw new BusinessException(CommentErrorCode.IDEMPOTENCY_MISMATCH);
			}
			return response;
		}

		var comment = comments.save(new Comment(sharedCourseId, memberId, content, now()));
		var response = toResponse(comment, memberId, members.findDisplayName(memberId).orElse(null), false);
		var replay = previous.orElseGet(() -> new CommentCreateRequestReplay(memberId, requestKey));
		replay.complete(jsonMapper.writeValueAsString(response), now().plus(REPLAY_LIFETIME));
		replays.save(replay);
		return response;
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public CommentResponse update(long memberId, long commentId, String content) {
		Comment comment = requireWritableComment(memberId, commentId);
		comment.changeContent(content, now());
		return toResponse(comment, memberId, members.findDisplayName(memberId).orElse(null), false);
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void delete(long memberId, long commentId) {
		comments.delete(requireWritableComment(memberId, commentId));
	}

	private Comment requireWritableComment(long memberId, long commentId) {
		// Resolve the immutable target first, then load the comment after locking its relationship.
		var target = comments.findTarget(commentId)
				.orElseThrow(() -> new BusinessException(CommentErrorCode.NOT_FOUND));
		if (target.getType() == CommentTargetType.COURSE) {
			requireOwnedCourse(memberId, target.getId(), CommentErrorCode.NOT_FOUND);
			throw new BusinessException(CommentErrorCode.ARCHIVED_READ_ONLY);
		}
		Couple couple = requireCurrentCouple(memberId, true, CommentErrorCode.NOT_FOUND);
		requireSharedCourse(target.getId(), couple, CommentErrorCode.NOT_FOUND);
		Comment comment = comments.findById(commentId)
				.orElseThrow(() -> new BusinessException(CommentErrorCode.NOT_FOUND));
		requireConnected(couple);
		if (comment.getAuthorMemberId() != memberId) {
			throw new BusinessException(CommentErrorCode.NOT_AUTHOR);
		}
		return comment;
	}

	private Couple requireCurrentCouple(long memberId, boolean lock, ErrorCode missing) {
		Instant now = now();
		var current = lock ? couples.findCurrentForUpdate(memberId, now) : couples.findCurrent(memberId, now);
		return current.filter(couple -> couple.getStatus() == CoupleStatus.CONNECTED
						|| couple.isInGrace(now()))
				.orElseThrow(() -> new BusinessException(missing));
	}

	private void requireSharedCourse(long sharedCourseId, Couple couple, ErrorCode missing) {
		if (!sharedCourses.existsByIdAndCoupleId(sharedCourseId, couple.getId())) {
			throw new BusinessException(missing);
		}
	}

	private void requireOwnedCourse(long memberId, long courseId, ErrorCode missing) {
		courses.findOwnedCourse(memberId, courseId).orElseThrow(() -> new BusinessException(missing));
	}

	private void requireConnected(Couple couple) {
		if (couple.getStatus() == CoupleStatus.GRACE) {
			throw new BusinessException(CoupleErrorCode.GRACE_READ_ONLY);
		}
	}

	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}

	private CommentResponse toResponse(Comment comment, long memberId, String displayName, boolean readOnly) {
		boolean mine = comment.getAuthorMemberId() == memberId;
		boolean canEdit = mine && !readOnly;
		return new CommentResponse(comment.getId(), comment.getTargetType(), comment.getTargetId(),
				new CommentResponse.Author(comment.getAuthorMemberId(), displayName), comment.getContent(),
				mine, comment.getArchivedAt() != null, comment.getArchivedAt(), readOnly, canEdit, canEdit,
				comment.getCreatedAt(), comment.getUpdatedAt());
	}
}
