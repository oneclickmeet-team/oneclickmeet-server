package com.hsu.oneclickmeet.domain.course.service;

import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;
import com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseShareResponse;
import com.hsu.oneclickmeet.domain.course.entity.Course;
import com.hsu.oneclickmeet.domain.course.entity.CourseShareRequest;
import com.hsu.oneclickmeet.domain.course.entity.SharedCourse;
import com.hsu.oneclickmeet.domain.course.exception.CourseErrorCode;
import com.hsu.oneclickmeet.domain.course.repository.CourseLibraryRepository;
import com.hsu.oneclickmeet.domain.course.repository.CourseRepository;
import com.hsu.oneclickmeet.domain.course.repository.CourseRequestLock;
import com.hsu.oneclickmeet.domain.course.repository.CourseShareRequestRepository;
import com.hsu.oneclickmeet.domain.course.repository.SharedCourseRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class CourseShareService {

	private static final Duration REPLAY_LIFETIME = Duration.ofHours(24);

	private final CourseRepository courses;
	private final CourseLibraryRepository courseLibraries;
	private final SharedCourseRepository sharedCourses;
	private final CourseShareRequestRepository shareRequests;
	private final CourseRequestLock requestLock;
	private final CoupleRepository couples;
	private final Clock clock;

	public CourseShareService(CourseRepository courses, CourseLibraryRepository courseLibraries,
			SharedCourseRepository sharedCourses,
			CourseShareRequestRepository shareRequests, CourseRequestLock requestLock, CoupleRepository couples,
			@Qualifier("coupleClock") Clock clock) {
		this.courses = courses;
		this.courseLibraries = courseLibraries;
		this.sharedCourses = sharedCourses;
		this.shareRequests = shareRequests;
		this.requestLock = requestLock;
		this.couples = couples;
		this.clock = clock;
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public CourseShareResponse share(long memberId, long sourceCourseId, UUID requestKey) {
		requireSourceOwnership(memberId, sourceCourseId);
		boolean acquired = requestLock.tryAcquireShare(memberId, sourceCourseId, requestKey);
		Couple couple = requireConnectedCouple(memberId, acquired);
		if (!acquired) {
			throw new BusinessException(CourseErrorCode.SHARE_IN_PROGRESS);
		}

		// Keep the original stable while copying its places and menus.
		Course source = courses.findOwnedCourseForUpdate(memberId, sourceCourseId)
				.orElseThrow(() -> new BusinessException(CourseErrorCode.NOT_FOUND));
		requireSourceOwnership(memberId, sourceCourseId);

		var previous = shareRequests.findByMemberIdAndSourceCourseIdAndRequestKey(memberId, sourceCourseId, requestKey);
		if (previous.isPresent() && previous.get().isReplayable(clock.instant())) {
			long sharedCourseId = previous.get().getSharedCourseId();
			if (!sharedCourses.existsByIdAndCoupleId(sharedCourseId, couple.getId())) {
				throw new BusinessException(CourseErrorCode.NOT_FOUND);
			}
			return new CourseShareResponse(sharedCourseId, sourceCourseId);
		}
		if (sharedCourses.existsByCoupleIdAndSourceCourseId(couple.getId(), sourceCourseId)) {
			throw new BusinessException(CourseErrorCode.ALREADY_SHARED);
		}

		Course snapshot = courses.save(source.copyContents());
		SharedCourse shared = sharedCourses.save(SharedCourse.create(snapshot, couple, sourceCourseId, memberId));
		CourseShareRequest request = previous.orElseGet(() -> new CourseShareRequest(memberId, sourceCourseId, requestKey));
		request.complete(shared.getId(), clock.instant().plus(REPLAY_LIFETIME));
		shareRequests.save(request);
		return new CourseShareResponse(shared.getId(), sourceCourseId);
	}

	private void requireSourceOwnership(long memberId, long sourceCourseId) {
		if (!courseLibraries.existsByMemberIdAndCourse_Id(memberId, sourceCourseId)) {
			throw new BusinessException(CourseErrorCode.NOT_FOUND);
		}
	}

	private Couple requireConnectedCouple(long memberId, boolean lock) {
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		// Serialize different-key shares for the same relationship before checking duplicates.
		var current = lock ? couples.findCurrentForUpdate(memberId, now) : couples.findCurrent(memberId, now);
		Couple couple = current.orElseThrow(() -> new BusinessException(CoupleErrorCode.STATE_CONFLICT));
		if (couple.getStatus() == CoupleStatus.GRACE) {
			throw new BusinessException(CoupleErrorCode.GRACE_READ_ONLY);
		}
		return couple;
	}
}
