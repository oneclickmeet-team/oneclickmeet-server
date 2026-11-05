package com.hsu.oneclickmeet.domain.recommendation.service;

import com.hsu.oneclickmeet.domain.recommendation.service.CourseBuilder.Course;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseBuilderTests {

	private static final double LAT = 37.5800;
	private static final double LNG = 127.0000;

	/** dLat 0.001도는 약 111m, dLng 0.001도는 약 88m다. */
	private static CourseSpot spot(String id, String group, double dLat, double dLng, boolean saved) {
		return new CourseSpot(id, "장소 " + id, "주소", null, "음식점", group, LAT + dLat, LNG + dLng, null,
				saved ? Long.valueOf(id.hashCode() & 0xFFFF) + 1 : null);
	}

	private static CourseSpot spot(String id, String group, double dLat, double dLng) {
		return spot(id, group, dLat, dLng, false);
	}

	private static List<String> ids(Course course) {
		return course.spots().stream().map(CourseSpot::providerPlaceId).toList();
	}

	@Test
	@DisplayName("도보 시간은 직선거리×1.3÷80m/분을 올림해 구한다")
	void estimatesWalkMinutes() {
		assertThat(WalkEstimator.meters(LAT, LNG, LAT + 0.001, LNG)).isBetween(110.0, 112.0);
		assertThat(WalkEstimator.walkMinutes(61.0)).isEqualTo(1);
		assertThat(WalkEstimator.walkMinutes(62.0)).isEqualTo(2);
		assertThat(WalkEstimator.walkMinutes(0.0)).isZero();
	}

	@Test
	@DisplayName("3~5곣으로 이어진 코스를 만들고 이웃 장소 사이는 도보 10분 이내다")
	void buildsConnectedCourseWithinWalkLimit() {
		List<CourseSpot> candidates = List.of(
			spot("a", "CE7", 0.000, 0.000), spot("b", "FD6", 0.002, 0.000), spot("c", "AT4", 0.004, 0.000),
			spot("d", "CT1", 0.006, 0.000), spot("e", "CE7", 0.008, 0.000), spot("f", "FD6", 0.0, 0.003));

		List<Course> courses = CourseBuilder.build(LAT, LNG, null, candidates);

		assertThat(courses).isNotEmpty().hasSizeLessThanOrEqualTo(3);
		for (Course course : courses) {
			assertThat(course.spots()).hasSizeBetween(3, 5);
			assertThat(course.legMinutes()).hasSize(course.spots().size() - 1)
				.allSatisfy(minutes -> assertThat(minutes).isLessThanOrEqualTo(10));
			assertThat(ids(course)).doesNotHaveDuplicates();
		}
	}

	@Test
	@DisplayName("분류가 다른 장소를 먼저 고른다")
	void prefersDifferentCategories() {
		List<CourseSpot> candidates = List.of(
			spot("cafe1", "CE7", 0.000, 0.000), spot("cafe2", "CE7", 0.0005, 0.000),
			spot("food", "FD6", 0.002, 0.000), spot("art", "CT1", 0.003, 0.000));

		Course first = CourseBuilder.build(LAT, LNG, null, candidates).get(0);

		assertThat(first.spots().stream().limit(3).map(CourseSpot::categoryGroupCode).distinct()).hasSize(3);
	}

	@Test
	@DisplayName("반경 1km 밖의 장소는 쓰지 않는다")
	void excludesSpotsOutsideRadius() {
		List<CourseSpot> candidates = List.of(
			spot("near1", "CE7", 0.000, 0.000), spot("near2", "FD6", 0.001, 0.000), spot("near3", "CT1", 0.002, 0.000),
			spot("far", "AT4", 0.012, 0.000));

		List<Course> courses = CourseBuilder.build(LAT, LNG, null, candidates);

		assertThat(courses).isNotEmpty();
		assertThat(courses).allSatisfy(course -> assertThat(ids(course)).doesNotContain("far"));
	}

	@Test
	@DisplayName("이웃 장소가 도보 10분보다 멀면 이어 붙이지 않아 3곣이 안 되면 코스가 없다")
	void doesNotChainSpotsBeyondTenMinutes() {
		// 0.0066도 ≈ 734m → 도보 12분
		List<CourseSpot> candidates = List.of(
			spot("a", "CE7", 0.0000, 0.0), spot("b", "FD6", 0.0066, 0.0), spot("c", "CT1", -0.0066, 0.0));

		assertThat(CourseBuilder.build(LAT, LNG, null, candidates)).isEmpty();
	}

	@Test
	@DisplayName("후보가 3곣 미만이면 코스가 없다")
	void returnsNothingWhenTooFewCandidates() {
		assertThat(CourseBuilder.build(LAT, LNG, null, List.of())).isEmpty();
		assertThat(CourseBuilder.build(LAT, LNG, null,
			List.of(spot("a", "CE7", 0, 0), spot("b", "FD6", 0.001, 0)))).isEmpty();
	}

	@Test
	@DisplayName("기준 장소는 모든 코스의 첫 장소이고 중복되지 않으며, 코스마다 두 번째 장소가 다르다")
	void anchorLeadsEveryCourse() {
		CourseSpot anchor = spot("anchor", "CE7", 0.000, 0.000, true);
		List<CourseSpot> candidates = new ArrayList<>(List.of(anchor));
		for (int i = 0; i < 8; i++) {
			candidates.add(spot("p" + i, i % 2 == 0 ? "FD6" : "CT1", 0.001 * (i + 1), 0.0));
		}

		List<Course> courses = CourseBuilder.build(LAT, LNG, anchor, candidates);

		assertThat(courses).hasSize(3);
		assertThat(courses).allSatisfy(course -> {
			assertThat(ids(course).get(0)).isEqualTo("anchor");
			assertThat(ids(course)).doesNotHaveDuplicates();
		});
		assertThat(courses.stream().map(course -> ids(course).get(1))).doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("보관함 장소를 같은 조건의 검색 결과보다 먼저 고른다")
	void prefersSavedPlaces() {
		List<CourseSpot> candidates = List.of(
			spot("search", "FD6", 0.001, 0.0), spot("mine", "FD6", 0.001, 0.0, true),
			spot("start", "CE7", 0.000, 0.0, true), spot("other", "CT1", 0.002, 0.0));

		Course first = CourseBuilder.build(LAT, LNG, null, candidates).get(0);

		assertThat(ids(first)).containsSubsequence("start", "mine");
	}

	@Test
	@DisplayName("같은 장소가 보관함과 검색 결과에 모두 있으면 보관함 쪽을 쓴다")
	void mergesDuplicatesPreferringSaved() {
		CourseSpot fromSearch = spot("x", "CE7", 0.0, 0.0);
		CourseSpot fromSaved = spot("x", "CE7", 0.0, 0.0, true);
		List<CourseSpot> candidates = List.of(fromSearch, fromSaved, spot("y", "FD6", 0.001, 0), spot("z", "CT1", 0.002, 0));

		List<Course> courses = CourseBuilder.build(LAT, LNG, null, candidates);

		assertThat(courses.get(0).spots().stream().filter(s -> s.providerPlaceId().equals("x")).findFirst().orElseThrow()
			.saved()).isTrue();
	}

	@Test
	@DisplayName("같은 입력이면 같은 결과가 나온다")
	void isDeterministic() {
		List<CourseSpot> candidates = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			candidates.add(spot("p" + i, i % 3 == 0 ? "CE7" : i % 3 == 1 ? "FD6" : "CT1", 0.0007 * i, 0.0005 * (i % 4)));
		}

		List<List<String>> first = CourseBuilder.build(LAT, LNG, null, candidates).stream().map(CourseBuilderTests::ids).toList();
		List<List<String>> second = CourseBuilder.build(LAT, LNG, null, candidates).stream().map(CourseBuilderTests::ids).toList();

		assertThat(first).isEqualTo(second);
	}

	@Test
	@DisplayName("코스는 최대 3개, 장소는 최대 5곣이고 같은 구성의 코스는 중복해서 만들지 않는다")
	void limitsCoursesAndPlaces() {
		List<CourseSpot> candidates = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			candidates.add(spot("p" + i, "G" + (i % 4), 0.0006 * i, 0.0004 * (i % 3)));
		}

		List<Course> courses = CourseBuilder.build(LAT, LNG, null, candidates);

		assertThat(courses).hasSizeLessThanOrEqualTo(3);
		assertThat(courses).allSatisfy(course -> assertThat(course.spots()).hasSizeLessThanOrEqualTo(5));
		assertThat(courses.stream().map(course -> course.spots().stream().map(CourseSpot::providerPlaceId).sorted().toList()))
			.doesNotHaveDuplicates();
	}
}
