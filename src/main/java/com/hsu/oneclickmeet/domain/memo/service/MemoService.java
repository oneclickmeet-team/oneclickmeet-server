package com.hsu.oneclickmeet.domain.memo.service;

import com.hsu.oneclickmeet.domain.course.exception.CourseErrorCode;
import com.hsu.oneclickmeet.domain.memo.dto.request.CreateMemoRequest;
import com.hsu.oneclickmeet.domain.memo.dto.response.MemoListResponse;
import com.hsu.oneclickmeet.domain.memo.dto.response.MemoResponse;
import com.hsu.oneclickmeet.domain.memo.entity.Memo;
import com.hsu.oneclickmeet.domain.memo.entity.MemoCreateReplay;
import com.hsu.oneclickmeet.domain.memo.entity.MemoTargetType;
import com.hsu.oneclickmeet.domain.memo.exception.MemoErrorCode;
import com.hsu.oneclickmeet.domain.memo.repository.MemoCreateReplayRepository;
import com.hsu.oneclickmeet.domain.memo.repository.MemoRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;
import com.hsu.oneclickmeet.global.persistence.MySqlTransactionLock;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MemoService {

	private static final Duration REPLAY_LIFETIME = Duration.ofHours(24);

	private final MemoRepository memos;
	private final MemoCreateReplayRepository replays;
	private final MemoTargetAccess targetAccess;
	private final MySqlTransactionLock locks;
	private final EntityManager entityManager;
	private final Clock clock;

	public MemoService(MemoRepository memos, MemoCreateReplayRepository replays, MemoTargetAccess targetAccess,
			MySqlTransactionLock locks, EntityManager entityManager, @Qualifier("coupleClock") Clock clock) {
		this.memos = memos;
		this.replays = replays;
		this.targetAccess = targetAccess;
		this.locks = locks;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public MemoResponse create(long memberId, UUID idempotencyKey, CreateMemoRequest request) {
		requireTargetAccess(memberId, request.targetType(), request.targetId());
		if (!locks.tryAcquire(":memo-create:" + memberId + ":" + idempotencyKey)) {
			throw new BusinessException(MemoErrorCode.CREATE_IN_PROGRESS);
		}
		if (request.targetType() == MemoTargetType.SHARED_COURSE
				&& !targetAccess.canAccessSharedCourseForWrite(memberId, request.targetId())) {
			throw new BusinessException(CourseErrorCode.NOT_FOUND);
		}
		String requestHash = fingerprint(request);
		var previous = replays.findByAuthorIdAndRequestKey(memberId, idempotencyKey);
		if (previous.isPresent() && previous.get().isReplayable(clock.instant())) {
			var replay = previous.get();
			if (!replay.getRequestHash().equals(requestHash)) {
				throw new BusinessException(MemoErrorCode.IDEMPOTENCY_KEY_CONFLICT);
			}
			// The verified retry supplies the original content, so the replay table stores no memo text.
			return new MemoResponse(replay.getMemoId(), request.targetType(), request.targetId(), request.content(),
					replay.getMemoCreatedAt(), replay.getMemoUpdatedAt());
		}

		Memo memo = insert(memberId, request);
		entityManager.refresh(memo);
		var replay = previous.orElseGet(() -> new MemoCreateReplay(memberId, idempotencyKey));
		replay.complete(requestHash, memo, clock.instant().plus(REPLAY_LIFETIME).plus(1, ChronoUnit.MICROS)
				.truncatedTo(ChronoUnit.MICROS));
		replays.save(replay);
		return toResponse(memo);
	}

	public MemoListResponse list(long memberId, MemoTargetType targetType, Long targetId, String cursor, int size) {
		if ((targetType == null) != (targetId == null) || size < 1 || size > 100) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
		if (targetType != null) {
			if (cursor != null) {
				throw new BusinessException(CommonErrorCode.BAD_REQUEST);
			}
			requireTargetAccess(memberId, targetType, targetId);
			var items = memos.findByAuthorIdAndTargetTypeAndTargetId(memberId, targetType, targetId)
					.stream().map(MemoService::toResponse).toList();
			return new MemoListResponse(items, null, false);
		}

		MemoCursor before = MemoCursor.decode(cursor, memberId, size);
		var limit = PageRequest.of(0, size + 1);
		var visible = new ArrayList<Memo>();
		// Continue past inaccessible rows until the requested page and its lookahead are filled.
		while (visible.size() <= size) {
			var candidates = memos.findPage(memberId, before == null ? null : before.createdAt(),
					before == null ? null : before.memoId(), limit);
			visible.addAll(targetAccess.accessible(memberId, candidates));
			if (candidates.size() < limit.getPageSize()) {
				break;
			}
			before = position(candidates.getLast());
		}
		boolean hasNext = visible.size() > size;
		List<MemoResponse> items = visible.stream().limit(size).map(MemoService::toResponse).toList();
		String nextCursor = hasNext ? position(visible.get(size - 1)).encode(memberId, size) : null;
		return new MemoListResponse(items, nextCursor, hasNext);
	}

	public MemoResponse detail(long memberId, long memoId) {
		return toResponse(requireAccessibleMemo(memberId, memos.findByIdAndAuthorId(memoId, memberId)));
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public MemoResponse update(long memberId, long memoId, String content) {
		Memo memo = requireWritableMemo(memberId, memoId);
		Instant updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
		memos.updateContent(memoId, content, updatedAt);
		return new MemoResponse(memoId, memo.getTargetType(), memo.getTargetId(), content,
				memo.getCreatedAt(), updatedAt);
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void delete(long memberId, long memoId) {
		memos.delete(requireWritableMemo(memberId, memoId));
	}

	private Memo insert(long memberId, CreateMemoRequest request) {
		if (memos.existsByAuthorIdAndTargetTypeAndTargetId(memberId, request.targetType(), request.targetId())) {
			throw new BusinessException(MemoErrorCode.ALREADY_EXISTS);
		}
		try {
			return memos.saveAndFlush(new Memo(memberId, request.targetType(), request.targetId(), request.content()));
		} catch (DataIntegrityViolationException exception) {
			// Concurrent requests with different keys can both pass the existence check.
			if (exception.getCause() instanceof ConstraintViolationException violation
					&& violation.getConstraintName() != null
					&& (violation.getConstraintName().equals(Memo.AUTHOR_TARGET_UNIQUE)
						|| violation.getConstraintName().endsWith("." + Memo.AUTHOR_TARGET_UNIQUE))) {
				throw new BusinessException(MemoErrorCode.ALREADY_EXISTS);
			}
			throw exception;
		}
	}

	private void requireTargetAccess(long memberId, MemoTargetType type, long id) {
		if (!targetAccess.canAccess(memberId, type, id)) {
			throw new BusinessException(switch (type) {
				case COURSE, SHARED_COURSE -> CourseErrorCode.NOT_FOUND;
				case PLACE, SHARED_PLACE -> MemoErrorCode.PLACE_NOT_FOUND;
			});
		}
	}

	private Memo requireAccessibleMemo(long memberId, Optional<Memo> found) {
		return found.filter(memo -> targetAccess.canAccess(memberId, memo.getTargetType(), memo.getTargetId()))
				.orElseThrow(() -> new BusinessException(MemoErrorCode.NOT_FOUND));
	}

	private Memo requireWritableMemo(long memberId, long memoId) {
		// Resolve the target without loading the memo before locking the relationship.
		var target = memos.findOwnedTarget(memoId, memberId)
				.orElseThrow(() -> new BusinessException(MemoErrorCode.NOT_FOUND));
		if (target.getType() == MemoTargetType.SHARED_COURSE) {
			if (!targetAccess.canAccessSharedCourseForWrite(memberId, target.getId())) {
				throw new BusinessException(MemoErrorCode.NOT_FOUND);
			}
			return memos.findForUpdateByIdAndAuthorId(memoId, memberId)
					.orElseThrow(() -> new BusinessException(MemoErrorCode.NOT_FOUND));
		}
		return requireAccessibleMemo(memberId, memos.findForUpdateByIdAndAuthorId(memoId, memberId));
	}

	private static MemoResponse toResponse(Memo memo) {
		return new MemoResponse(memo.getId(), memo.getTargetType(), memo.getTargetId(), memo.getContent(),
				memo.getCreatedAt(), memo.getUpdatedAt());
	}

	private static MemoCursor position(Memo memo) {
		return new MemoCursor(memo.getCreatedAt(), memo.getId());
	}

	private static String fingerprint(CreateMemoRequest request) {
		try {
			String payload = request.targetType().name() + ":" + request.targetId() + ":" + request.content();
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
		}
	}
}
