package com.hsu.oneclickmeet.domain.course.service;

import com.hsu.oneclickmeet.domain.comment.service.CommentArchiveService;
import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.course.entity.CourseLibrary;
import com.hsu.oneclickmeet.domain.course.entity.SharedCourse;
import com.hsu.oneclickmeet.domain.course.entity.SharedCourseCancellationCopy;
import com.hsu.oneclickmeet.domain.course.repository.CourseLibraryRepository;
import com.hsu.oneclickmeet.domain.course.repository.CourseRepository;
import com.hsu.oneclickmeet.domain.course.repository.SharedCourseCancellationCopyRepository;
import com.hsu.oneclickmeet.domain.course.repository.SharedCourseRepository;
import com.hsu.oneclickmeet.domain.memo.service.MemoTransferService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class SharedCourseCopyService {

	private final CoupleRepository couples;
	private final SharedCourseRepository sharedCourses;
	private final CourseRepository courses;
	private final CourseLibraryRepository libraries;
	private final SharedCourseCancellationCopyRepository copyReceipts;
	private final MemoTransferService memoTransfer;
	private final CommentArchiveService commentArchive;
	private final Clock clock;

	public SharedCourseCopyService(CoupleRepository couples, SharedCourseRepository sharedCourses,
			CourseRepository courses, CourseLibraryRepository libraries,
			SharedCourseCancellationCopyRepository copyReceipts, MemoTransferService memoTransfer,
			CommentArchiveService commentArchive, @Qualifier("coupleClock") Clock clock) {
		this.couples = couples;
		this.sharedCourses = sharedCourses;
		this.courses = courses;
		this.libraries = libraries;
		this.copyReceipts = copyReceipts;
		this.memoTransfer = memoTransfer;
		this.commentArchive = commentArchive;
		this.clock = clock;
	}

	@Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
	public void copyAfterGrace(long coupleId, long sharedCourseId) {
		Couple couple = couples.findByIdForUpdate(coupleId).orElse(null);
		if (couple == null || couple.getStatus() != CoupleStatus.ENDED
				|| copyReceipts.existsBySharedCourseId(sharedCourseId)) {
			return;
		}
		// Retain the expired source so its receipt cannot be replayed as a successful cancellation.
		sharedCourses.findByIdAndCoupleId(sharedCourseId, coupleId)
				.ifPresent(shared -> copyForMembers(shared, couple, clock.instant().truncatedTo(ChronoUnit.MICROS)));
	}

	// Caller holds the source couple lock inside the transaction that saves both recipients' copies.
	void copyForMembers(SharedCourse shared, Couple couple, Instant archivedAt) {
		long firstCopyId = savePersonalCopy(shared, couple.getFirstMemberId());
		long secondCopyId = savePersonalCopy(shared, couple.getSecondMemberId());
		commentArchive.copyForUnshare(shared.getId(), firstCopyId, secondCopyId, archivedAt);
	}

	private long savePersonalCopy(SharedCourse shared, long memberId) {
		var copy = courses.save(shared.getCourse().copyContents());
		libraries.save(CourseLibrary.create(copy, memberId));
		copyReceipts.save(new SharedCourseCancellationCopy(copy.getId(), shared.getId(), memberId));
		memoTransfer.copyForUnshare(shared.getId(), memberId, copy.getId());
		return copy.getId();
	}
}
