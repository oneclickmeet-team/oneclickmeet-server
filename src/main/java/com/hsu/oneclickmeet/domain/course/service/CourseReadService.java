package com.hsu.oneclickmeet.domain.course.service;

import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseDetailResponse;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseDetailResponse.Menu;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseDetailResponse.Place;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseDetailResponse.Warning;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseListResponse;
import com.hsu.oneclickmeet.domain.course.entity.Course;
import com.hsu.oneclickmeet.domain.course.entity.CoursePlace;
import com.hsu.oneclickmeet.domain.course.exception.CourseErrorCode;
import com.hsu.oneclickmeet.domain.course.repository.CourseRepository;
import com.hsu.oneclickmeet.domain.course.repository.SharedCourseRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CourseReadService {

	private static final int WALKING_LIMIT_MINUTES = 10;

	private final CourseRepository courseRepository;
	private final CoupleRepository couples;
	private final SharedCourseRepository sharedCourses;
	private final Clock clock;

	public CourseReadService(CourseRepository courseRepository, CoupleRepository couples,
			SharedCourseRepository sharedCourses, @Qualifier("coupleClock") Clock clock) {
		this.courseRepository = courseRepository;
		this.couples = couples;
		this.sharedCourses = sharedCourses;
		this.clock = clock;
	}

	public CourseListResponse list(long memberId, String cursor, int size) {
		CourseCursor before = CourseCursor.decode(cursor, memberId, size);
		Long coupleId = findCurrentCoupleId(memberId).orElse(null);
		var summaries = courseRepository.findOwnedSummaries(memberId, coupleId,
				before == null ? null : before.createdAt(), before == null ? null : before.courseId(),
				PageRequest.of(0, size + 1));
		boolean hasNext = summaries.size() > size;
		var items = summaries.stream().limit(size)
				.map(summary -> new CourseListResponse.Item(summary.getCourseId(), summary.getCourseName(),
						summary.getPlaceCount(), summary.getOriginSharedCourseId(),
						Objects.requireNonNull(summary.getCreatedAt(), "Course has no creation timestamp"), summary.isShared()))
				.toList();
		String nextCursor = null;
		if (hasNext) {
			var last = items.getLast();
			nextCursor = new CourseCursor(last.createdAt(), last.courseId()).encode(memberId, size);
		}
		return new CourseListResponse(items, nextCursor, hasNext);
	}

	public CourseDetailResponse detail(long memberId, long courseId) {
		Course course = courseRepository.findOwnedCourse(memberId, courseId)
				.orElseThrow(() -> new BusinessException(CourseErrorCode.NOT_FOUND));
		boolean isShared = findCurrentCoupleId(memberId)
				.map(coupleId -> sharedCourses.existsByCoupleIdAndSourceCourseId(coupleId, courseId))
				.orElse(false);
		return toDetail(course, isShared);
	}

	private Optional<Long> findCurrentCoupleId(long memberId) {
		return couples.findCurrent(memberId, clock.instant().truncatedTo(ChronoUnit.MICROS)).map(Couple::getId);
	}

	static CourseDetailResponse toDetail(Course course, boolean isShared) {
		List<Place> places = course.getPlaces().stream().map(CourseReadService::toPlace).toList();
		long total = places.stream().mapToLong(Place::selectedSubtotal).sum();
		long unselectedPlaces = places.stream().filter(place -> place.menuList().stream().noneMatch(Menu::selected)).count();
		boolean hasWalkingEstimate = places.stream().skip(1)
				.anyMatch(place -> place.estimatedWalkingMinutesFromPrevious() != null);
		return new CourseDetailResponse(course.getId(), course.getName(), places, total, unselectedPlaces,
				hasWalkingEstimate ? "ESTIMATED" : null, walkingWarnings(places), course.getCreatedAt(), course.getUpdatedAt(),
				isShared);
	}

	private static Place toPlace(CoursePlace place) {
		List<Menu> menus = place.getMenuList().stream()
				.map(menu -> new Menu(menu.getId(), menu.getName(), menu.getPrice(), menu.getSelected(), menu.getUpdatedAt()))
				.toList();
		long subtotal = menus.stream()
				.filter(Menu::selected)
				.map(Menu::price)
				.filter(Objects::nonNull)
				.mapToLong(Integer::longValue)
				.sum();
		var snapshot = place.getSnapshot();
		if (snapshot == null) {
			return new Place(place.getId(), place.getOriginalPlaceId(), place.getName(), null, null, null, menus, subtotal,
					null, null, null);
		}
		return new Place(place.getId(), place.getOriginalPlaceId(), place.getName(), snapshot.getLatitude(),
				snapshot.getLongitude(), null, menus, subtotal, snapshot.getProvider(), snapshot.getProviderPlaceId(),
				snapshot.getAddress());
	}

	static List<Warning> walkingWarnings(List<Place> places) {
		return places.stream().skip(1)
				.filter(place -> place.estimatedWalkingMinutesFromPrevious() != null
						&& place.estimatedWalkingMinutesFromPrevious() > WALKING_LIMIT_MINUTES)
				.map(place -> new Warning("WALKING_TIME_EXCEEDED", place.coursePlaceId(), place.estimatedWalkingMinutesFromPrevious()))
				.toList();
	}
}
