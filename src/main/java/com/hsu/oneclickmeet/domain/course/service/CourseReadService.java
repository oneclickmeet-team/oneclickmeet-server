package com.hsu.oneclickmeet.domain.course.service;

import com.hsu.oneclickmeet.domain.course.dto.response.CourseDetailResponse;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseDetailResponse.Menu;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseDetailResponse.Place;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseDetailResponse.Warning;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseListResponse;
import com.hsu.oneclickmeet.domain.course.entity.Course;
import com.hsu.oneclickmeet.domain.course.entity.CoursePlace;
import com.hsu.oneclickmeet.domain.course.exception.CourseErrorCode;
import com.hsu.oneclickmeet.domain.course.repository.CourseRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseReadService {

	private static final int WALKING_LIMIT_MINUTES = 10;

	private final CourseRepository courseRepository;

	public CourseListResponse list(long memberId, String cursor, int size) {
		CourseCursor before = CourseCursor.decode(cursor, memberId, size);
		var summaries = courseRepository.findOwnedSummaries(memberId, before == null ? null : before.createdAt(),
				before == null ? null : before.courseId(), PageRequest.of(0, size + 1));
		boolean hasNext = summaries.size() > size;
		var items = summaries.stream().limit(size)
				.map(summary -> new CourseListResponse.Item(summary.getCourseId(), summary.getCourseName(),
						summary.getPlaceCount(), null, Objects.requireNonNull(summary.getCreatedAt(), "Course has no creation timestamp")))
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
		List<Place> places = course.getPlaces().stream().map(CourseReadService::toPlace).toList();
		long total = places.stream().mapToLong(Place::selectedSubtotal).sum();
		long unselectedPlaces = places.stream().filter(place -> place.menuList().stream().noneMatch(Menu::selected)).count();
		boolean hasWalkingEstimate = places.stream().skip(1)
				.anyMatch(place -> place.estimatedWalkingMinutesFromPrevious() != null);
		return new CourseDetailResponse(course.getId(), course.getName(), places, total, unselectedPlaces,
				hasWalkingEstimate ? "ESTIMATED" : null, walkingWarnings(places), course.getCreatedAt(), course.getUpdatedAt());
	}

	private static Place toPlace(CoursePlace place) {
		List<Menu> menus = place.getMenuList().stream()
				.map(menu -> new Menu(menu.getId(), menu.getName(), menu.getPrice(), menu.getSelected(), menu.getUpdatedAt()))
				.toList();
		long subtotal = menus.stream().filter(Menu::selected).mapToLong(Menu::price).sum();
		// The saved place snapshot supplies the original ID and name. Coordinates and route estimates are pending.
		return new Place(place.getId(), place.getOriginalPlaceId(), place.getName(), null, null, null, menus, subtotal);
	}

	static List<Warning> walkingWarnings(List<Place> places) {
		return places.stream().skip(1)
				.filter(place -> place.estimatedWalkingMinutesFromPrevious() != null
						&& place.estimatedWalkingMinutesFromPrevious() > WALKING_LIMIT_MINUTES)
				.map(place -> new Warning("WALKING_TIME_EXCEEDED", place.coursePlaceId(), place.estimatedWalkingMinutesFromPrevious()))
				.toList();
	}
}
