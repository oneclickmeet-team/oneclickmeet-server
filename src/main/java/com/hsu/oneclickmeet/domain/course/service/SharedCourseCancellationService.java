package com.hsu.oneclickmeet.domain.course.service;

import com.hsu.oneclickmeet.domain.comment.service.CommentArchiveService;
import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;
import com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.course.entity.CourseLibrary;
import com.hsu.oneclickmeet.domain.course.entity.SharedCourse;
import com.hsu.oneclickmeet.domain.course.entity.SharedCourseCancellationCopy;
import com.hsu.oneclickmeet.domain.course.exception.CourseErrorCode;
import com.hsu.oneclickmeet.domain.course.repository.CourseLibraryRepository;
import com.hsu.oneclickmeet.domain.course.repository.CourseRepository;
import com.hsu.oneclickmeet.domain.course.repository.SharedCourseCancellationCopyRepository;
import com.hsu.oneclickmeet.domain.course.repository.SharedCourseRepository;
import com.hsu.oneclickmeet.domain.memo.service.MemoTransferService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class SharedCourseCancellationService {

	private final SharedCourseRepository sharedCourses;
	private final CoupleRepository couples;
	private final CourseRepository courses;
	private final CourseLibraryRepository libraries;
	private final SharedCourseCancellationCopyRepository cancellationCopies;
	private final MemoTransferService memoTransfer;
	private final CommentArchiveService commentArchive;
	private final Clock clock;

	public SharedCourseCancellationService(SharedCourseRepository sharedCourses, CoupleRepository couples,
			CourseRepository courses, CourseLibraryRepository libraries,
			SharedCourseCancellationCopyRepository cancellationCopies, MemoTransferService memoTransfer,
			CommentArchiveService commentArchive, @Qualifier("coupleClock") Clock clock) {
		this.sharedCourses = sharedCourses;
		this.couples = couples;
		this.courses = courses;
		this.libraries = libraries;
		this.cancellationCopies = cancellationCopies;
		this.memoTransfer = memoTransfer;
		this.commentArchive = commentArchive;
		this.clock = clock;
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void cancel(long memberId, long sharedCourseId) {
		var coupleId = sharedCourses.findCoupleId(sharedCourseId);
		if (coupleId.isEmpty()) {
			requireCompletedCancellation(memberId, sharedCourseId);
			return;
		}

		// Lock the same relationship row as shared edits before loading the latest snapshot.
		Couple couple = couples.findMemberCoupleForUpdate(memberId, coupleId.get())
				.orElseThrow(() -> new BusinessException(CourseErrorCode.NOT_FOUND));
		var current = sharedCourses.findByIdAndCoupleId(sharedCourseId, couple.getId());
		if (current.isEmpty()) {
			requireCompletedCancellation(memberId, sharedCourseId);
			return;
		}

		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		if (couple.isInGrace(now)) {
			throw new BusinessException(CoupleErrorCode.GRACE_READ_ONLY);
		}
		if (couple.getStatus() != CoupleStatus.CONNECTED) {
			throw new BusinessException(CoupleErrorCode.STATE_CONFLICT);
		}

		SharedCourse shared = current.get();
		long firstCopyId = savePersonalCopy(shared, couple.getFirstMemberId());
		long secondCopyId = savePersonalCopy(shared, couple.getSecondMemberId());
		commentArchive.copyForUnshare(sharedCourseId, firstCopyId, secondCopyId, now);
		sharedCourses.delete(shared);
	}

	private long savePersonalCopy(SharedCourse shared, long memberId) {
		var copy = courses.save(shared.getCourse().copyContents());
		libraries.save(CourseLibrary.create(copy, memberId));
		cancellationCopies.save(new SharedCourseCancellationCopy(copy.getId(), shared.getId(), memberId));
		memoTransfer.copyForUnshare(shared.getId(), memberId, copy.getId());
		return copy.getId();
	}

	private void requireCompletedCancellation(long memberId, long sharedCourseId) {
		if (!cancellationCopies.existsBySharedCourseIdAndMemberId(sharedCourseId, memberId)) {
			throw new BusinessException(CourseErrorCode.NOT_FOUND);
		}
	}
}
