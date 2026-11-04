package com.hsu.oneclickmeet.domain.course.service;

import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.MenuRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.PlaceRequest;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseDetailResponse.Menu;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseDetailResponse.Place;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseListResponse.Item;
import com.hsu.oneclickmeet.domain.course.exception.CourseErrorCode;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=2")
@ActiveProfiles("test")
class CourseReadServiceTests {

	private static final CoursePlaceLookup AVAILABLE_PLACES = (memberId, placeId) -> Optional.of("장소 " + placeId);

	@Autowired
	private CourseSaveService saveService;

	@Autowired
	private CourseReadService readService;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void persistsAuditTimestampsRequiredByCourseReads() {
		long courseId = save(301L, "감사 시각 코스");

		assertThat(jdbc.queryForObject("select created_at from course where course_id = ?", Timestamp.class, courseId))
			.isNotNull();
		assertThat(jdbc.queryForObject("select updated_at from course where course_id = ?", Timestamp.class, courseId))
			.isNotNull();
		assertThat(jdbc.queryForList("""
			select m.updated_at from course_place_item m join course_place p on m.place_id = p.place_id
			where p.course_id = ?
			""", Timestamp.class, courseId)).hasSize(1).doesNotContainNull();
	}

	@Test
	void listsOnlyTheOwnersCoursesOnceEvenWithMultipleLibraryLinks() {
		long memberId = 302L;
		long owned = save(memberId, "내 코스");
		long unowned = save(303L, "소유자 없는 코스");
		long otherOwnersCourse = save(303L, "다른 회원 코스");
		long duplicateLink = save(303L, "중복 소유 연결용 코스");
		jdbc.update("update course_library set member_id = null where course_id = ?", unowned);
		jdbc.update("update course_library set course_id = ?, member_id = ? where course_id = ?",
			owned, memberId, duplicateLink);

		var page = readService.list(memberId, null, 1);
		assertThat(page.items()).singleElement().satisfies(item -> {
			assertThat(item.courseId()).isEqualTo(owned);
			assertThat(item.courseName()).isEqualTo("내 코스");
			assertThat(item.placeCount()).isEqualTo(3L);
			assertThat(item.originSharedCourseId()).isNull();
			assertThat(item.createdAt()).isNotNull();
		});
		assertThat(page.hasNext()).isFalse();
		assertThat(page.nextCursor()).isNull();
		assertThat(readService.detail(memberId, owned).courseId()).isEqualTo(owned);
		assertThat(readService.list(303L, null, 1).items()).extracting(Item::courseId)
			.containsExactly(otherOwnersCourse);

		var emptyPage = readService.list(304L, null, 20);
		assertThat(emptyPage.items()).isEmpty();
		assertThat(emptyPage.hasNext()).isFalse();
		assertThat(emptyPage.nextCursor()).isNull();
	}

	@Test
	void paginatesByCreationTimeAndIdAcrossEqualTimestamps() {
		long memberId = 305L;
		long newest = save(memberId, "시간상 최신 코스");
		long lowerTiedId = save(memberId, "같은 시각의 작은 ID");
		long oldest = save(memberId, "시간상 최초 코스");
		long higherTiedId = save(memberId, "같은 시각의 큰 ID");
		setCreatedAt(newest, "2026-01-01 12:00:02.123456");
		setCreatedAt(lowerTiedId, "2026-01-01 12:00:01.123456");
		setCreatedAt(higherTiedId, "2026-01-01 12:00:01.123456");
		setCreatedAt(oldest, "2026-01-01 12:00:00.123456");

		var first = readService.list(memberId, null, 2);
		assertThat(first.items()).extracting(Item::courseId).containsExactly(newest, higherTiedId);
		assertThat(first.hasNext()).isTrue();
		assertThat(first.nextCursor()).isNotBlank();

		var last = readService.list(memberId, first.nextCursor(), 2);
		assertThat(last.items()).extracting(Item::courseId).containsExactly(lowerTiedId, oldest);
		assertThat(last.hasNext()).isFalse();
		assertThat(last.nextCursor()).isNull();
	}

	@Test
	void rejectsMalformedAndOutOfScopeCursors() {
		long memberId = 306L;
		save(memberId, "첫 코스");
		save(memberId, "둘째 코스");
		String cursor = readService.list(memberId, null, 1).nextCursor();
		assertThat(cursor).isNotBlank();

		assertBadCursor(memberId, "", 1);
		assertBadCursor(memberId, "잘못된 cursor", 1);
		assertBadCursor(307L, cursor, 1);
		assertBadCursor(memberId, cursor, 2);
	}

	@Test
	void readsStoredOrderIdsAndSelectedAmountsWithLongTotals() {
		long memberId = 308L;
		var request = new CreateCourseRequest("선택 메뉴 코스", List.of(
			new PlaceRequest(50_503L, List.of(
				new MenuRequest(null, "파스타", 1_000_000_000, true),
				new MenuRequest(null, "피자", 1_000_000_000, true),
				new MenuRequest(null, "커피", 1_000_000_000, true),
				new MenuRequest(null, "샐러드", 30_000, false))),
			new PlaceRequest(50_501L, List.of()),
			new PlaceRequest(50_502L, List.of(new MenuRequest(null, "주스", 15_000, false))),
			new PlaceRequest(50_504L, List.of(new MenuRequest(null, "물", 0, true)))));
		long courseId = saveService.save(memberId, UUID.randomUUID(), request, AVAILABLE_PLACES);
		jdbc.update("update course_place set visit_order = 5 - visit_order where course_id = ?", courseId);
		List<Long> coursePlaceIds = jdbc.queryForList("""
			select place_id from course_place where course_id = ? order by visit_order
			""", Long.class, courseId);
		long lastPlaceId = coursePlaceIds.getLast();
		jdbc.update("update course_place_item set menu_order = 5 - menu_order where place_id = ?", lastPlaceId);
		List<Long> menuIds = jdbc.queryForList("""
			select menu_id from course_place_item where place_id = ? order by menu_order
			""", Long.class, lastPlaceId);

		var detail = readService.detail(memberId, courseId);
		assertThat(detail.courseId()).isEqualTo(courseId);
		assertThat(detail.courseName()).isEqualTo("선택 메뉴 코스");
		assertThat(detail.createdAt()).isNotNull();
		assertThat(detail.updatedAt()).isNotNull();
		assertThat(detail.selectedTotalPrice()).isEqualTo(3_000_000_000L);
		assertThat(detail.placesWithoutSelectedMenusCount()).isEqualTo(2L);
		assertThat(detail.places()).extracting(Place::coursePlaceId).containsExactlyElementsOf(coursePlaceIds);
		assertThat(detail.places()).extracting(Place::placeId, Place::placeName, Place::selectedSubtotal)
			.containsExactly(tuple(50_504L, "장소 50504", 0L), tuple(50_502L, "장소 50502", 0L),
				tuple(50_501L, "장소 50501", 0L), tuple(50_503L, "장소 50503", 3_000_000_000L));
		assertThat(detail.places().get(2).menuList()).isEmpty();
		List<Menu> menus = detail.places().getLast().menuList();
		assertThat(menus).extracting(Menu::menuId).containsExactlyElementsOf(menuIds);
		assertThat(menus).extracting(Menu::menuName, Menu::price, Menu::selected)
			.containsExactly(tuple("샐러드", 30_000, false), tuple("커피", 1_000_000_000, true),
				tuple("피자", 1_000_000_000, true), tuple("파스타", 1_000_000_000, true));
		assertThat(menus).extracting(Menu::updatedAt).doesNotContainNull();
		assertThat(detail.places()).allSatisfy(place -> {
			assertThat(place.latitude()).isNull();
			assertThat(place.longitude()).isNull();
			assertThat(place.estimatedWalkingMinutesFromPrevious()).isNull();
		});
		assertThat(detail.walkingTimeSource()).isNull();
		assertThat(detail.warnings()).isEmpty();
	}

	@Test
	void countsEveryPlaceWhenNothingIsSelected() {
		long memberId = 309L;
		var request = new CreateCourseRequest("미선택 코스", List.of(
			new PlaceRequest(503L, List.of(new MenuRequest(null, "파스타", 25_000, false))),
			new PlaceRequest(501L, List.of()), new PlaceRequest(502L, List.of())));
		long courseId = saveService.save(memberId, UUID.randomUUID(), request, AVAILABLE_PLACES);

		var detail = readService.detail(memberId, courseId);
		assertThat(detail.selectedTotalPrice()).isZero();
		assertThat(detail.placesWithoutSelectedMenusCount()).isEqualTo(3L);
	}

	@Test
	void treatsMissingUnownedAndOtherMembersCoursesAsNotFound() {
		long memberId = 310L;
		long otherOwnersCourse = save(311L, "다른 회원 상세");
		long unowned = save(memberId, "소유자 없는 상세");
		jdbc.update("update course_library set member_id = null where course_id = ?", unowned);

		assertNotFound(memberId, otherOwnersCourse);
		assertNotFound(memberId, unowned);
		assertNotFound(memberId, Long.MAX_VALUE);
	}

	@Test
	void readsOneCourseAfterReplayAndNoneAfterFailedSave() {
		long memberId = 312L;
		UUID key = UUID.randomUUID();
		var request = request("재시도 코스");
		long courseId = saveService.save(memberId, key, request, AVAILABLE_PLACES);
		assertThat(saveService.save(memberId, key, request, AVAILABLE_PLACES)).isEqualTo(courseId);

		assertThatThrownBy(() -> saveService.save(memberId, UUID.randomUUID(), request("저장 실패 코스"),
			(owner, placeId) -> placeId == 501L
				? Optional.of("가".repeat(256))
				: AVAILABLE_PLACES.findPlaceName(owner, placeId)))
			.isInstanceOf(DataIntegrityViolationException.class);

		assertThat(readService.list(memberId, null, 20).items()).extracting(Item::courseId).containsExactly(courseId);
		assertThat(readService.detail(memberId, courseId).courseName()).isEqualTo("재시도 코스");
	}

	private void setCreatedAt(long courseId, String timestamp) {
		jdbc.update("update course set created_at = ? where course_id = ?", Timestamp.valueOf(timestamp), courseId);
	}

	private void assertBadCursor(long memberId, String cursor, int size) {
		assertThatThrownBy(() -> readService.list(memberId, cursor, size))
			.isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.BAD_REQUEST));
	}

	private void assertNotFound(long memberId, long courseId) {
		assertThatThrownBy(() -> readService.detail(memberId, courseId))
			.isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(CourseErrorCode.NOT_FOUND));
	}

	private long save(long memberId, String courseName) {
		return saveService.save(memberId, UUID.randomUUID(), request(courseName), AVAILABLE_PLACES);
	}

	private static CreateCourseRequest request(String courseName) {
		return new CreateCourseRequest(courseName, List.of(
			new PlaceRequest(503L, List.of(new MenuRequest(null, "파스타", 25_000, true))),
			new PlaceRequest(501L, List.of()),
			new PlaceRequest(502L, List.of())));
	}
}
