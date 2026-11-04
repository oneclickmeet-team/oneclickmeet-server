package com.hsu.oneclickmeet.domain.course.service;

import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.MenuRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.PlaceRequest;
import com.hsu.oneclickmeet.domain.course.exception.CourseErrorCode;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=2")
@ActiveProfiles("test")
class CourseSaveServiceTests {

	private static final CoursePlaceLookup AVAILABLE_PLACES = (memberId, placeId) -> Optional.of("장소 " + placeId);

	@Autowired
	private CourseSaveService service;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void savesOwnerAndCourseGraphInRequestOrder() {
		long memberId = 101L;
		long courseId = service.save(memberId, UUID.randomUUID(), request("  연남 코스  "), (owner, placeId) -> {
			assertThat(owner).isEqualTo(memberId);
			return AVAILABLE_PLACES.findPlaceName(owner, placeId);
		});

		assertThat(jdbc.queryForObject("select course_name from course where course_id = ?", String.class, courseId))
				.isEqualTo("연남 코스");
		assertThat(jdbc.queryForObject("select member_id from course_library where course_id = ?", Long.class, courseId))
				.isEqualTo(memberId);
		assertThat(jdbc.query("""
				select original_place_id, place_name, visit_order from course_place where course_id = ? order by visit_order
				""", (row, index) -> tuple(row.getLong(1), row.getString(2), row.getInt(3)), courseId))
				.containsExactly(tuple(503L, "장소 503", 1), tuple(501L, "장소 501", 2), tuple(502L, "장소 502", 3));
		assertThat(jdbc.query("""
				select p.original_place_id, m.menu_name, m.price, m.menu_selected, m.menu_order
				from course_place_item m join course_place p on m.place_id = p.place_id
				where p.course_id = ? order by p.visit_order, m.menu_order
				""", (row, index) -> tuple(row.getLong(1), row.getString(2), row.getInt(3), row.getBoolean(4), row.getInt(5)), courseId))
				.containsExactly(tuple(503L, "파스타", 25000, true, 1), tuple(503L, "샐러드", 12000, false, 2));
	}

	@Test
	void rejectsInvalidInputBeforeLookingUpPlaces() {
		assertThatThrownBy(() -> service.save(106L, UUID.randomUUID(),
				new CreateCourseRequest("장소가 부족한 코스", List.of(new PlaceRequest(501L, List.of()))), (owner, placeId) -> {
					throw new AssertionError("입력 검증을 통과한 요청만 장소를 조회할 수 있습니다.");
				}))
				.isInstanceOf(ConstraintViolationException.class);
	}

	@Test
	void replaysTheSameRequestAndScopesTheKeyToTheOwner() {
		long memberId = 102L;
		UUID key = UUID.randomUUID();
		CreateCourseRequest original = request("산책 코스");
		long courseId = service.save(memberId, key, original, AVAILABLE_PLACES);

		CoursePlaceLookup unexpectedLookup = (owner, placeId) -> {
			throw new AssertionError("이미 저장된 코스를 다시 구성하면 안 됩니다.");
		};
		assertThat(service.save(memberId, key, request("  산책 코스  "), unexpectedLookup)).isEqualTo(courseId);
		assertThatThrownBy(() -> service.save(memberId, key, request("다른 코스"), unexpectedLookup))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(CourseErrorCode.IDEMPOTENCY_KEY_CONFLICT));
		assertThat(service.save(103L, key, original, AVAILABLE_PLACES)).isNotEqualTo(courseId);
		assertThat(jdbc.queryForObject("select count(*) from course_library where member_id = ?", Long.class, memberId))
				.isEqualTo(1L);
	}

	@Test
	void rejectsConcurrentRequestWhileTheFirstSaveIsRunning() throws Exception {
		long memberId = 104L;
		UUID key = UUID.randomUUID();
		CreateCourseRequest request = request("동시 저장 코스");
		CountDownLatch lookupStarted = new CountDownLatch(1);
		CountDownLatch finishLookup = new CountDownLatch(1);
		try (var executor = Executors.newSingleThreadExecutor()) {
			var first = executor.submit(() -> service.save(memberId, key, request, (owner, placeId) -> {
				lookupStarted.countDown();
				await(finishLookup);
				return AVAILABLE_PLACES.findPlaceName(owner, placeId);
			}));
			try {
				await(lookupStarted);
				assertThatThrownBy(() -> service.save(memberId, key, request, AVAILABLE_PLACES))
						.isInstanceOfSatisfying(BusinessException.class,
								exception -> assertThat(exception.getErrorCode()).isEqualTo(CourseErrorCode.SAVE_IN_PROGRESS));
			} finally {
				finishLookup.countDown();
			}
			long courseId = first.get(10, TimeUnit.SECONDS);
			assertLockReleased(memberId, key);
			assertThat(service.save(memberId, key, request, AVAILABLE_PLACES)).isEqualTo(courseId);
		}
	}

	@Test
	void rollsBackTheCourseAndAllowsTheSameKeyAfterADatabaseFailure() {
		long memberId = 105L;
		UUID key = UUID.randomUUID();
		CreateCourseRequest request = request("저장 재시도 코스");
		Long placeCount = jdbc.queryForObject("select count(*) from course_place", Long.class);
		Long menuCount = jdbc.queryForObject("select count(*) from course_place_item", Long.class);

		assertThatThrownBy(() -> service.save(memberId, key, request, (owner, placeId) ->
				placeId == 501L ? Optional.of("가".repeat(256)) : AVAILABLE_PLACES.findPlaceName(owner, placeId)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.rootCause().hasMessageContaining("Data too long for column 'place_name'");

		assertThat(jdbc.queryForObject("select count(*) from course where course_name = ?", Long.class, request.courseName()))
				.isZero();
		assertThat(jdbc.queryForObject("select count(*) from course_place", Long.class)).isEqualTo(placeCount);
		assertThat(jdbc.queryForObject("select count(*) from course_place_item", Long.class)).isEqualTo(menuCount);
		assertThat(jdbc.queryForObject("select count(*) from course_library where member_id = ?", Long.class, memberId))
				.isZero();
		assertThat(jdbc.queryForObject("select count(*) from course_save_requests where member_id = ?", Long.class, memberId))
				.isZero();
		assertLockReleased(memberId, key);
		assertThat(service.save(memberId, key, request, AVAILABLE_PLACES)).isPositive();
	}

	private void assertLockReleased(long memberId, UUID key) {
		assertThat(jdbc.queryForObject("select is_free_lock(sha2(concat(database(), ':course-save:', ?), 256))",
				Integer.class, memberId + ":" + key)).isEqualTo(1);
	}

	private static CreateCourseRequest request(String courseName) {
		return new CreateCourseRequest(courseName, List.of(
				new PlaceRequest(503L, List.of(new MenuRequest(null, "파스타", 25000, true),
						new MenuRequest(null, "샐러드", 12000, false))),
				new PlaceRequest(501L, List.of()),
				new PlaceRequest(502L, List.of())));
	}

	private static void await(CountDownLatch latch) {
		try {
			assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError(exception);
		}
	}
}
