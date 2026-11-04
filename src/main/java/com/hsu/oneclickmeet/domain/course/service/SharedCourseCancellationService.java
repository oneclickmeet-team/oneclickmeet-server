package com.hsu.oneclickmeet.domain.course.service;

import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;
import com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.course.entity.SharedCourse;
import com.hsu.oneclickmeet.domain.course.exception.CourseErrorCode;
import com.hsu.oneclickmeet.domain.course.repository.SharedCourseCancellationCopyRepository;
import com.hsu.oneclickmeet.domain.course.repository.SharedCourseRepository;
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
	private final SharedCourseCancellationCopyRepository cancellationCopies;
	private final SharedCourseCopyService copyService;
	private final Clock clock;

	public SharedCourseCancellationService(SharedCourseRepository sharedCourses, CoupleRepository couples,
			SharedCourseCancellationCopyRepository cancellationCopies, SharedCourseCopyService copyService,
			@Qualifier("coupleClock") Clock clock) {
		this.sharedCourses = sharedCourses;
		this.couples = couples;
		this.cancellationCopies = cancellationCopies;
		this.copyService = copyService;
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
		copyService.copyForMembers(shared, couple, now);
		sharedCourses.delete(shared);
	}

	private void requireCompletedCancellation(long memberId, long sharedCourseId) {
		if (!cancellationCopies.existsBySharedCourseIdAndMemberId(sharedCourseId, memberId)) {
			throw new BusinessException(CourseErrorCode.NOT_FOUND);
		}
	}
}
