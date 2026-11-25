package com.hsu.oneclickmeet.domain.memo.service;

import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.course.repository.CourseLibraryRepository;
import com.hsu.oneclickmeet.domain.course.repository.SharedCourseRepository;
import com.hsu.oneclickmeet.domain.memo.entity.Memo;
import com.hsu.oneclickmeet.domain.memo.entity.MemoTargetType;
import com.hsu.oneclickmeet.domain.place.repository.SavedPlaceRepository;
import com.hsu.oneclickmeet.domain.place.repository.SharedPlaceRepository;
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
	private final SavedPlaceRepository savedPlaces;
	private final SharedPlaceRepository sharedPlaces;
	private final CoupleRepository couples;
	private final Clock clock;

	public MemoTargetAccess(CourseLibraryRepository courses, SharedCourseRepository sharedCourses,
			SavedPlaceRepository savedPlaces, SharedPlaceRepository sharedPlaces,
			CoupleRepository couples, @Qualifier("coupleClock") Clock clock) {
		this.courses = courses;
		this.sharedCourses = sharedCourses;
		this.savedPlaces = savedPlaces;
		this.sharedPlaces = sharedPlaces;
		this.couples = couples;
		this.clock = clock;
	}

	public boolean canAccess(long memberId, MemoTargetType targetType, long targetId) {
		return switch (targetType) {
			case COURSE -> courses.existsByMemberIdAndCourse_Id(memberId, targetId);
			case PLACE -> savedPlaces.existsByIdAndMemberId(targetId, memberId);
			case SHARED_COURSE, SHARED_PLACE -> currentCouple(memberId)
					.map(couple -> hasSharedTarget(couple.getId(), targetType, targetId))
					.orElse(false);
		};
	}

	public boolean canAccessSharedTargetForWrite(long memberId, MemoTargetType targetType, long targetId) {
		return couples.findCurrentForUpdate(memberId, now())
				.filter(couple -> couple.getStatus() == CoupleStatus.CONNECTED || couple.isInGrace(now()))
				.map(couple -> hasSharedTarget(couple.getId(), targetType, targetId))
				.orElse(false);
	}

	public List<Memo> accessible(long memberId, List<Memo> candidates) {
		List<Memo> owned = candidates.stream().filter(memo -> memo.getAuthorId() == memberId).toList();
		Set<Long> courseIds = targetIds(owned, MemoTargetType.COURSE);
		Set<Long> sharedCourseIds = targetIds(owned, MemoTargetType.SHARED_COURSE);
		Set<Long> savedPlaceIds = targetIds(owned, MemoTargetType.PLACE);
		Set<Long> sharedPlaceIds = targetIds(owned, MemoTargetType.SHARED_PLACE);
		Optional<Couple> couple = sharedCourseIds.isEmpty() && sharedPlaceIds.isEmpty()
				? Optional.empty() : currentCouple(memberId);
		Set<Long> accessibleCourses = courseIds.isEmpty() ? Set.of() : courses.findOwnedCourseIds(memberId, courseIds);
		Set<Long> accessibleSavedPlaces = savedPlaceIds.isEmpty()
				? Set.of() : savedPlaces.findOwnedSavedPlaceIds(memberId, savedPlaceIds);
		Set<Long> accessibleSharedCourses = sharedCourseIds.isEmpty() ? Set.of() : couple
				.map(current -> sharedCourses.findSharedCourseIds(current.getId(), sharedCourseIds))
				.orElseGet(Set::of);
		Set<Long> accessibleSharedPlaces = sharedPlaceIds.isEmpty() ? Set.of() : couple
				.map(current -> sharedPlaces.findSharedPlaceIds(current.getId(), sharedPlaceIds))
				.orElseGet(Set::of);
		return owned.stream().filter(memo -> switch (memo.getTargetType()) {
			case COURSE -> accessibleCourses.contains(memo.getTargetId());
			case SHARED_COURSE -> accessibleSharedCourses.contains(memo.getTargetId());
			case PLACE -> accessibleSavedPlaces.contains(memo.getTargetId());
			case SHARED_PLACE -> accessibleSharedPlaces.contains(memo.getTargetId());
		}).toList();
	}

	private boolean hasSharedTarget(long coupleId, MemoTargetType targetType, long targetId) {
		return switch (targetType) {
			case SHARED_COURSE -> sharedCourses.existsByIdAndCoupleId(targetId, coupleId);
			case SHARED_PLACE -> sharedPlaces.existsByIdAndCouple_Id(targetId, coupleId);
			case COURSE, PLACE -> false;
		};
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
