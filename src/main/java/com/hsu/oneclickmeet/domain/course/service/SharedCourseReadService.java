package com.hsu.oneclickmeet.domain.course.service;

import com.hsu.oneclickmeet.domain.couple.dto.response.PartnerResponse;
import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;
import com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleMemberRepository;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseDetailResponse;
import com.hsu.oneclickmeet.domain.course.dto.response.SharedCourseDetailResponse;
import com.hsu.oneclickmeet.domain.course.dto.response.SharedCourseListResponse;
import com.hsu.oneclickmeet.domain.course.entity.SharedCourse;
import com.hsu.oneclickmeet.domain.course.exception.CourseErrorCode;
import com.hsu.oneclickmeet.domain.course.repository.SharedCourseRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongFunction;

@Service
@Transactional(readOnly = true)
public class SharedCourseReadService {

	private final SharedCourseRepository sharedCourses;
	private final CoupleRepository couples;
	private final CoupleMemberRepository members;
	private final Clock clock;

	public SharedCourseReadService(SharedCourseRepository sharedCourses, CoupleRepository couples,
			CoupleMemberRepository members, @Qualifier("coupleClock") Clock clock) {
		this.sharedCourses = sharedCourses;
		this.couples = couples;
		this.members = members;
		this.clock = clock;
	}

	public SharedCourseListResponse list(long memberId, String cursor, int size) {
		Couple couple = requireCurrentCouple(memberId, CoupleErrorCode.STATE_CONFLICT);
		long coupleId = couple.getId();
		CourseCursor before = CourseCursor.decodeShared(cursor, coupleId, size);
		var summaries = sharedCourses.findSummaries(coupleId, before == null ? null : before.createdAt(),
				before == null ? null : before.courseId(), PageRequest.of(0, size + 1));
		boolean hasNext = summaries.size() > size;
		var items = summaries.stream().limit(size)
				.map(summary -> new SharedCourseListResponse.Item(summary.getSharedCourseId(), summary.getCourseName(),
						summary.getPlaceCount(), summary.getSharedAt()))
				.toList();
		String nextCursor = null;
		if (hasNext) {
			var last = items.getLast();
			nextCursor = new CourseCursor(last.sharedAt(), last.sharedCourseId()).encodeShared(coupleId, size);
		}
		boolean readOnly = couple.getStatus() == CoupleStatus.GRACE;
		return new SharedCourseListResponse(coupleId, couple.getStatus(), readOnly,
				readOnly ? couple.getGraceEndsAt() : null, items, nextCursor, hasNext);
	}

	public SharedCourseDetailResponse detail(long memberId, long sharedCourseId) {
		Couple couple = requireCurrentCouple(memberId, CourseErrorCode.NOT_FOUND);
		var shared = sharedCourses.findByIdAndCoupleId(sharedCourseId, couple.getId())
				.orElseThrow(() -> new BusinessException(CourseErrorCode.NOT_FOUND));
		var course = CourseReadService.toDetail(shared.getCourse());
		Map<Long, PartnerResponse> editors = new HashMap<>();
		LongFunction<PartnerResponse> editorFor = id -> editors.computeIfAbsent(id,
				member -> new PartnerResponse(member, members.findDisplayName(member).orElse(null)));
		var editor = editorFor.apply(Objects.requireNonNullElse(shared.getLastEditedByMemberId(), shared.getSharedByMemberId()));
		var menuEditors = menuEditors(shared);
		var places = course.places().stream().map(place -> toSharedPlace(place, menuEditors, editorFor)).toList();
		boolean readOnly = couple.getStatus() == CoupleStatus.GRACE;
		return new SharedCourseDetailResponse(shared.getId(), couple.getId(), couple.getStatus(), readOnly,
				readOnly ? couple.getGraceEndsAt() : null, shared.getStructureVersion(), editor,
				course.courseName(), places, course.selectedTotalPrice(), course.placesWithoutSelectedMenusCount(),
				course.walkingTimeSource(), course.warnings(), course.createdAt(),
				shared.getLastEditedAt() == null ? course.updatedAt() : shared.getLastEditedAt());
	}

	private Couple requireCurrentCouple(long memberId, ErrorCode errorCode) {
		return couples.findCurrent(memberId, clock.instant().truncatedTo(ChronoUnit.MICROS))
				.orElseThrow(() -> new BusinessException(errorCode));
	}

	private static Map<Long, Long> menuEditors(SharedCourse shared) {
		Map<Long, Long> editors = new HashMap<>();
		for (var place : shared.getCourse().getPlaces()) {
			for (var menu : place.getMenuList()) {
				editors.put(menu.getId(), Objects.requireNonNullElse(menu.getLastEditedByMemberId(), shared.getSharedByMemberId()));
			}
		}
		return editors;
	}

	private static SharedCourseDetailResponse.Place toSharedPlace(CourseDetailResponse.Place place,
			Map<Long, Long> menuEditors, LongFunction<PartnerResponse> editorFor) {
		var menus = place.menuList().stream()
				.map(menu -> new SharedCourseDetailResponse.Menu(menu.menuId(), menu.menuName(), menu.price(),
						menu.selected(), menu.updatedAt(), editorFor.apply(menuEditors.get(menu.menuId()))))
				.toList();
		return new SharedCourseDetailResponse.Place(place.coursePlaceId(), place.placeId(), place.placeName(),
				place.latitude(), place.longitude(), place.estimatedWalkingMinutesFromPrevious(), menus,
				place.selectedSubtotal());
	}
}
