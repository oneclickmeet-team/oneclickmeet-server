package com.hsu.oneclickmeet.domain.memo.service;

import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.course.repository.CourseLibraryRepository;
import com.hsu.oneclickmeet.domain.course.repository.SharedCourseRepository;
import com.hsu.oneclickmeet.domain.memo.entity.Memo;
import com.hsu.oneclickmeet.domain.memo.entity.MemoTargetType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MemoTargetAccess {

	private final CourseLibraryRepository courses;
	private final SharedCourseRepository sharedCourses;
	private final CoupleRepository couples;
	private final Clock clock;

	public MemoTargetAccess(CourseLibraryRepository courses, SharedCourseRepository sharedCourses,
			CoupleRepository couples, @Qualifier("coupleClock") Clock clock) {
		this.courses = courses;
		this.sharedCourses = sharedCourses;
		this.couples = couples;
		this.clock = clock;
	}

	public boolean canAccess(long memberId, MemoTargetType targetType, long targetId) {
		return switch (targetType) {
			case COURSE -> courses.existsByMemberIdAndCourse_Id(memberId, targetId);
			case SHARED_COURSE -> currentCouple(memberId)
					.map(couple -> sharedCourses.existsByIdAndCoupleId(targetId, couple.getId()))
					.orElse(false);
			// TODO(#19): Connect the saved-place and shared-place access checks after their contracts are agreed.
			case PLACE, SHARED_PLACE -> false;
		};
	}

	public boolean canAccessSharedCourseForWrite(long memberId, long sharedCourseId) {
		return couples.findCurrentForUpdate(memberId, now())
				.filter(couple -> couple.getStatus() == CoupleStatus.CONNECTED || couple.isInGrace(now()))
				.map(couple -> sharedCourses.existsByIdAndCoupleId(sharedCourseId, couple.getId()))
				.orElse(false);
	}

	public List<Memo> accessible(long memberId, List<Memo> candidates) {
		List<Memo> owned = candidates.stream().filter(memo -> memo.getAuthorId() == memberId).toList();
		Set<Long> courseIds = targetIds(owned, MemoTargetType.COURSE);
		Set<Long> sharedCourseIds = targetIds(owned, MemoTargetType.SHARED_COURSE);
		Set<Long> accessibleCourses = courseIds.isEmpty() ? Set.of() : courses.findOwnedCourseIds(memberId, courseIds);
		Set<Long> accessibleSharedCourses = sharedCourseIds.isEmpty() ? Set.of() : currentCouple(memberId)
				.map(couple -> sharedCourses.findSharedCourseIds(couple.getId(), sharedCourseIds))
				.orElseGet(Set::of);
		return owned.stream().filter(memo -> switch (memo.getTargetType()) {
			case COURSE -> accessibleCourses.contains(memo.getTargetId());
			case SHARED_COURSE -> accessibleSharedCourses.contains(memo.getTargetId());
			case PLACE, SHARED_PLACE -> false;
		}).toList();
	}

	private Optional<Couple> currentCouple(long memberId) {
		return couples.findCurrent(memberId, now());
	}

	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}

	private Set<Long> targetIds(List<Memo> memos, MemoTargetType targetType) {
		return memos.stream().filter(memo -> memo.getTargetType() == targetType)
				.map(Memo::getTargetId).collect(Collectors.toSet());
	}
}
