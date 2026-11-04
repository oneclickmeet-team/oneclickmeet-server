package com.hsu.oneclickmeet.domain.memo.service;

import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.course.repository.CourseLibraryRepository;
import com.hsu.oneclickmeet.domain.course.repository.SharedCourseRepository;
import com.hsu.oneclickmeet.domain.memo.entity.Memo;
import com.hsu.oneclickmeet.domain.memo.entity.MemoTargetType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class MemoTargetAccessTests {

	private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");
	private static final long AUTHOR = 11L;
	private static final long PARTNER = 12L;
	private static final long COUPLE_ID = 21L;

	private final CourseLibraryRepository courses = mock(CourseLibraryRepository.class);
	private final SharedCourseRepository sharedCourses = mock(SharedCourseRepository.class);
	private final CoupleRepository couples = mock(CoupleRepository.class);
	private final MemoTargetAccess access = new MemoTargetAccess(courses, sharedCourses, couples, Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void personalCourseRequiresOwnership() {
		given(courses.existsByMemberIdAndCourse_Id(AUTHOR, 101L)).willReturn(true);

		assertThat(access.canAccess(AUTHOR, MemoTargetType.COURSE, 101L)).isTrue();
		assertThat(access.canAccess(PARTNER, MemoTargetType.COURSE, 101L)).isFalse();
	}

	@Test
	void sharedCourseRequiresTheCurrentCoupleAndAcceptsGrace() {
		Couple grace = Couple.connect(AUTHOR, PARTNER, NOW.minusSeconds(3600));
		ReflectionTestUtils.setField(grace, "id", COUPLE_ID);
		grace.disconnect(NOW);
		given(couples.findCurrent(AUTHOR, NOW)).willReturn(Optional.of(grace));
		given(sharedCourses.existsByIdAndCoupleId(101L, COUPLE_ID)).willReturn(true);

		assertThat(access.canAccess(AUTHOR, MemoTargetType.SHARED_COURSE, 101L)).isTrue();
		assertThat(access.canAccess(AUTHOR, MemoTargetType.SHARED_COURSE, 102L)).isFalse();
	}

	@Test
	void sharedCourseCannotBeAccessedWithoutACurrentCouple() {
		assertThat(access.canAccess(AUTHOR, MemoTargetType.SHARED_COURSE, 101L)).isFalse();

		verifyNoInteractions(sharedCourses);
	}

	@Test
	void placeTargetsAreDeniedUntilTheirAccessContractIsAvailable() {
		assertThat(access.canAccess(AUTHOR, MemoTargetType.PLACE, 101L)).isFalse();
		assertThat(access.canAccess(AUTHOR, MemoTargetType.SHARED_PLACE, 101L)).isFalse();

		verifyNoInteractions(courses, sharedCourses, couples);
	}

	@Test
	void onlyKeepsTheAuthorsAccessibleMemosInCandidateOrder() {
		Memo shared = new Memo(AUTHOR, MemoTargetType.SHARED_COURSE, 201L, "공동 코스 메모");
		Memo privateCourse = new Memo(AUTHOR, MemoTargetType.COURSE, 101L, "개인 코스 메모");
		Memo inaccessibleCourse = new Memo(AUTHOR, MemoTargetType.COURSE, 102L, "삭제된 코스 메모");
		Memo place = new Memo(AUTHOR, MemoTargetType.PLACE, 101L, "장소 메모");
		Memo otherAuthor = new Memo(PARTNER, MemoTargetType.SHARED_COURSE, 201L, "상대방 메모");
		Couple current = Couple.connect(AUTHOR, PARTNER, NOW);
		ReflectionTestUtils.setField(current, "id", COUPLE_ID);
		given(couples.findCurrent(AUTHOR, NOW)).willReturn(Optional.of(current));
		given(courses.findOwnedCourseIds(AUTHOR, Set.of(101L, 102L))).willReturn(Set.of(101L));
		given(sharedCourses.findSharedCourseIds(COUPLE_ID, Set.of(201L))).willReturn(Set.of(201L));

		assertThat(access.accessible(AUTHOR, List.of(shared, inaccessibleCourse, place, privateCourse, otherAuthor)))
				.containsExactly(shared, privateCourse);
	}
}
