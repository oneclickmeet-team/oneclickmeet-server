package com.hsu.oneclickmeet.domain.recommendation.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 후보 장소로 코스를 만든다(같은 입력이면 항상 같은 결과).
 * 코스는 장소 3~5곣이며 이웃한 장소 사이 도보 10분 이내, 중심에서 반경 1km 이내 장소만 쓴다.
 * 기준 장소가 있으면 모든 코스의 시작에 반드시 포함한다. 앞선 코스에서 쓰지 않은 장소를 먼저,
 * 분류가 다른 장소를, 보관함 장소를, 그다음 가까운 장소를 고른다.
 */
final class CourseBuilder {

	static final int MIN_PLACES = 3;
	static final int MAX_PLACES = 5;
	static final int MAX_COURSES = 3;
	static final int MAX_LEG_MINUTES = 10;
	static final double RADIUS_METERS = 1000.0;

	private CourseBuilder() {
	}

	record Course(List<CourseSpot> spots, List<Integer> legMinutes) {

		int totalMinutes() {
			return legMinutes.stream().mapToInt(Integer::intValue).sum();
		}
	}

	static List<Course> build(double centerLat, double centerLng, CourseSpot anchor, List<CourseSpot> candidates) {
		List<CourseSpot> pool = poolOf(centerLat, centerLng, anchor, candidates);
		List<Course> courses = new ArrayList<>();
		Set<String> usedSecond = new HashSet<>();
		Set<Set<String>> seen = new HashSet<>();
		Set<String> usedStarts = new HashSet<>();
		Set<String> usedInCourses = new HashSet<>();
		for (int i = 0; i < MAX_COURSES * 2 && courses.size() < MAX_COURSES; i++) {
			CourseSpot start = anchor != null ? anchor : startAt(pool, usedStarts);
			if (start == null) {
				break;
			}
			usedStarts.add(start.providerPlaceId());
			Course course = chain(start, pool, usedSecond, usedInCourses);
			if (course.spots().size() >= MIN_PLACES) {
				Set<String> ids = new HashSet<>();
				course.spots().forEach(spot -> ids.add(spot.providerPlaceId()));
				if (seen.add(ids)) {
					courses.add(course);
					usedInCourses.addAll(ids);
				}
			}
			if (anchor != null && course.spots().size() < 2) {
				break;
			}
		}
		return courses;
	}

	private static List<CourseSpot> poolOf(double lat, double lng, CourseSpot anchor, List<CourseSpot> candidates) {
		Map<String, CourseSpot> unique = new LinkedHashMap<>();
		for (CourseSpot spot : candidates) {
			if (anchor != null && spot.providerPlaceId().equals(anchor.providerPlaceId())) {
				continue;
			}
			if (WalkEstimator.meters(lat, lng, spot.lat(), spot.lng()) > RADIUS_METERS) {
				continue;
			}
			unique.merge(spot.providerPlaceId(), spot, (existing, other) -> existing.saved() ? existing : other);
		}
		List<CourseSpot> pool = new ArrayList<>(unique.values());
		pool.sort(Comparator
				.comparing((CourseSpot spot) -> !spot.saved())
				.thenComparingDouble(spot -> WalkEstimator.meters(lat, lng, spot.lat(), spot.lng()))
				.thenComparing(CourseSpot::providerPlaceId));
		return pool;
	}

	private static CourseSpot startAt(List<CourseSpot> pool, Set<String> usedStarts) {
		return pool.stream().filter(spot -> !usedStarts.contains(spot.providerPlaceId())).findFirst().orElse(null);
	}

	private static Course chain(CourseSpot start, List<CourseSpot> pool, Set<String> usedSecond,
			Set<String> usedInCourses) {
		List<CourseSpot> spots = new ArrayList<>(List.of(start));
		List<Integer> legs = new ArrayList<>();
		Set<String> groups = new HashSet<>();
		groups.add(String.valueOf(start.categoryGroupCode()));
		while (spots.size() < MAX_PLACES) {
			CourseSpot current = spots.get(spots.size() - 1);
			boolean choosingSecond = spots.size() == 1;
			CourseSpot next = pool.stream()
					.filter(spot -> spots.stream().noneMatch(s -> s.providerPlaceId().equals(spot.providerPlaceId())))
					.filter(spot -> !(choosingSecond && usedSecond.contains(spot.providerPlaceId())))
					.filter(spot -> WalkEstimator.walkMinutes(current, spot) <= MAX_LEG_MINUTES)
					.min(Comparator
							.comparing((CourseSpot spot) -> usedInCourses.contains(spot.providerPlaceId()))
							.thenComparing(spot -> groups.contains(String.valueOf(spot.categoryGroupCode())))
							.thenComparing(spot -> !spot.saved())
							.thenComparingInt(spot -> WalkEstimator.walkMinutes(current, spot))
							.thenComparing(CourseSpot::providerPlaceId))
					.orElse(null);
			if (next == null) {
				break;
			}
			if (choosingSecond) {
				usedSecond.add(next.providerPlaceId());
			}
			legs.add(WalkEstimator.walkMinutes(current, next));
			groups.add(String.valueOf(next.categoryGroupCode()));
			spots.add(next);
		}
		return new Course(spots, legs);
	}
}
