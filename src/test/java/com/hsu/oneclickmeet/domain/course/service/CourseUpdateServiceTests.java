package com.hsu.oneclickmeet.domain.course.service;

import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.UpdateCourseRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.UpdateCourseRequest.MenuRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.UpdateCourseRequest.PlaceRequest;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseDetailResponse;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseDetailResponse.Menu;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseDetailResponse.Place;
import com.hsu.oneclickmeet.domain.course.exception.CourseErrorCode;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class CourseUpdateServiceTests {

	@Autowired
	private CourseSaveService saveService;

	@Autowired
	private CourseReadService readService;

	@Autowired
	private CourseUpdateService updateService;

	@Autowired
	private CourseDeleteService deleteService;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private CoursePlaceLookup placeLookup;

	@Test
	void reordersPlacesAndMenusWithoutReplacingTheirIds() {
		long owner = 401L;
		var original = saveCourse(owner, UUID.randomUUID());
		var first = original.places().getFirst();
		var second = original.places().get(1);
		var third = original.places().getLast();
		var coffee = first.menuList().getFirst();
		var cake = first.menuList().getLast();
		var reordered = new PlaceRequest(first.coursePlaceId(), first.placeId(),
			List.of(copyMenu(cake), copyMenu(coffee)));

		var result = updateService.update(owner, original.courseId(), UUID.randomUUID(),
			new UpdateCourseRequest(original.courseName(), List.of(copyPlace(third), reordered, copyPlace(second))));

		var updated = readService.detail(owner, original.courseId());
		assertThat(result.courseId()).isEqualTo(original.courseId());
		assertThat(result.updatedAt()).isAfter(original.updatedAt()).isEqualTo(updated.updatedAt());
		assertThat(updated.places()).extracting(Place::coursePlaceId)
			.containsExactly(third.coursePlaceId(), first.coursePlaceId(), second.coursePlaceId());
		assertThat(updated.places().get(1).menuList())
			.extracting(Menu::menuId, Menu::menuName, Menu::price, Menu::selected)
			.containsExactly(tuple(cake.menuId(), cake.menuName(), cake.price(), cake.selected()),
				tuple(coffee.menuId(), coffee.menuName(), coffee.price(), coffee.selected()));
		verifyNoInteractions(placeLookup);
	}

	@Test
	void replacesAnOmittedPlaceAndItsMenusAndEditsTheRetainedMenu() {
		long owner = 402L;
		var original = saveCourse(owner, UUID.randomUUID());
		var removedPlace = original.places().get(1);
		var retainedPlace = original.places().getFirst();
		var retainedMenu = retainedPlace.menuList().getFirst();
		var omittedMenu = retainedPlace.menuList().getLast();
		var edited = new PlaceRequest(retainedPlace.coursePlaceId(), retainedPlace.placeId(),
			List.of(new MenuRequest(retainedMenu.menuId(), "라떼", 8000, false)));
		var request = new UpdateCourseRequest("장소 교체 코스",
			List.of(edited, newPlace(), copyPlace(original.places().getLast())));
		when(placeLookup.findPlaceName(owner, 504L)).thenReturn(Optional.of("새 카페"));

		updateService.update(owner, original.courseId(), UUID.randomUUID(), request);

		var updated = readService.detail(owner, original.courseId());
		assertThat(updated.courseName()).isEqualTo("장소 교체 코스");
		assertThat(updated.places().getFirst().menuList())
			.extracting(Menu::menuId, Menu::menuName, Menu::price, Menu::selected)
			.containsExactly(tuple(retainedMenu.menuId(), "라떼", 8000, false));
		var replacement = updated.places().get(1);
		assertThat(replacement.coursePlaceId()).isNotEqualTo(removedPlace.coursePlaceId());
		assertThat(replacement.placeId()).isEqualTo(504L);
		assertThat(replacement.placeName()).isEqualTo("새 카페");
		assertThat(replacement.menuList()).singleElement().satisfies(menu -> {
			assertThat(menu.menuId()).isNotEqualTo(removedPlace.menuList().getFirst().menuId());
			assertThat(menu.menuName()).isEqualTo("주스");
		});
		assertThat(jdbc.queryForObject("select count(*) from course_place where place_id = ?",
			Long.class, removedPlace.coursePlaceId())).isZero();
		assertThat(jdbc.queryForObject("select count(*) from course_place_item where place_id = ? or menu_id = ?",
			Long.class, removedPlace.coursePlaceId(), omittedMenu.menuId())).isZero();
	}

	@Test
	void replaysTheOriginalUpdateResultAfterAnotherUpdateAndRejectsADifferentBodyWithTheSameKey() {
		long owner = 403L;
		UUID key = UUID.randomUUID();
		var original = saveCourse(owner, key);
		var anotherCourse = saveCourse(owner, UUID.randomUUID());
		var request = replaceMiddlePlace(original);
		when(placeLookup.findPlaceName(owner, 504L)).thenReturn(Optional.of("새 카페"));

		var result = updateService.update(owner, original.courseId(), key, request);
		var firstUpdate = readService.detail(owner, original.courseId());
		updateService.update(owner, original.courseId(), UUID.randomUUID(), copyCourse(firstUpdate, "다시 저장한 코스"));
		updateService.update(owner, anotherCourse.courseId(), key, copyCourse(anotherCourse, "별도 코스의 수정"));
		var latest = readService.detail(owner, original.courseId());

		assertThat(updateService.update(owner, original.courseId(), key, request)).isEqualTo(result);
		assertThat(readService.detail(owner, original.courseId())).isEqualTo(latest);
		assertThat(latest.courseName()).isEqualTo("다시 저장한 코스");
		assertThat(latest.places()).isEqualTo(firstUpdate.places());
		assertThatThrownBy(() -> updateService.update(owner, original.courseId(), key,
			new UpdateCourseRequest("다른 요청", request.places())))
			.isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(CourseErrorCode.IDEMPOTENCY_KEY_CONFLICT));
		verify(placeLookup).findPlaceName(owner, 504L);
	}

	@Test
	void rejectsTheSameKeyWhileAnUpdateIsRunningAndReplaysWhenItFinishes() throws Exception {
		long owner = 407L;
		var original = saveCourse(owner, UUID.randomUUID());
		UUID key = UUID.randomUUID();
		var request = replaceMiddlePlace(original);
		var lookupStarted = new CountDownLatch(1);
		var finishLookup = new CountDownLatch(1);
		when(placeLookup.findPlaceName(owner, 504L)).thenAnswer(invocation -> {
			lookupStarted.countDown();
			assertThat(finishLookup.await(10, TimeUnit.SECONDS)).isTrue();
			return Optional.of("새 카페");
		});

		try (var executor = Executors.newSingleThreadExecutor()) {
			var first = executor.submit(() -> updateService.update(owner, original.courseId(), key, request));
			try {
				assertThat(lookupStarted.await(10, TimeUnit.SECONDS)).isTrue();
				assertThatThrownBy(() -> updateService.update(owner, original.courseId(), key, request))
					.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(CourseErrorCode.SAVE_IN_PROGRESS));
			} finally {
				finishLookup.countDown();
			}
			var result = first.get(10, TimeUnit.SECONDS);
			assertThat(updateService.update(owner, original.courseId(), key, request)).isEqualTo(result);
		}
		verify(placeLookup).findPlaceName(owner, 504L);
	}

	@Test
	void rejectsReplayAfterTheOwnerDeletesTheCourse() {
		long owner = 408L;
		var original = saveCourse(owner, UUID.randomUUID());
		UUID key = UUID.randomUUID();
		var request = copyCourse(original, "수정한 코스");
		updateService.update(owner, original.courseId(), key, request);

		deleteService.delete(owner, original.courseId());

		assertThatThrownBy(() -> updateService.update(owner, original.courseId(), key, request))
			.isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(CourseErrorCode.NOT_FOUND));
	}

	@Test
	void rollsBackAnUpdateThatFailsInTheDatabaseAndAllowsTheSameKeyOnRetry() {
		long owner = 404L;
		var original = saveCourse(owner, UUID.randomUUID());
		UUID key = UUID.randomUUID();
		var request = replaceMiddlePlace(original);
		when(placeLookup.findPlaceName(owner, 504L))
			.thenReturn(Optional.of("가".repeat(256))).thenReturn(Optional.of("새 카페"));

		assertThatThrownBy(() -> updateService.update(owner, original.courseId(), key, request))
			.isInstanceOf(DataIntegrityViolationException.class)
			.rootCause().hasMessageContaining("Data too long for column 'place_name'");
		assertThat(readService.detail(owner, original.courseId())).isEqualTo(original);

		updateService.update(owner, original.courseId(), key, request);
		assertThat(readService.detail(owner, original.courseId()).places().get(1).placeName()).isEqualTo("새 카페");
	}

	@Test
	void rejectsAReplacementWhenTheSourcePlaceIsUnavailableToTheOwner() {
		long owner = 409L;
		var original = saveCourse(owner, UUID.randomUUID());
		when(placeLookup.findPlaceName(owner, 504L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> updateService.update(owner, original.courseId(), UUID.randomUUID(),
			replaceMiddlePlace(original)))
			.isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(CourseErrorCode.SOURCE_PLACE_NOT_FOUND));
		assertThat(readService.detail(owner, original.courseId())).isEqualTo(original);
	}

	@Test
	void rejectsOtherMembersAndReferencesToAnotherCoursesPlaceOrMenu() {
		long owner = 405L;
		long otherMember = 406L;
		var original = saveCourse(owner, UUID.randomUUID());
		var otherCourse = saveCourse(otherMember, UUID.randomUUID());
		var originalPlaces = original.places();
		var otherPlace = otherCourse.places().getFirst();
		var first = originalPlaces.getFirst();

		assertThatThrownBy(() -> updateService.update(otherMember, original.courseId(), UUID.randomUUID(),
			replaceMiddlePlace(original)))
			.isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(CourseErrorCode.NOT_FOUND));

		for (var foreign : List.of(copyPlace(otherPlace),
			new PlaceRequest(first.coursePlaceId(), first.placeId(), List.of(copyMenu(otherPlace.menuList().getFirst()))))) {
			var request = new UpdateCourseRequest("다른 코스의 구성 사용",
				List.of(foreign, copyPlace(originalPlaces.get(1)), copyPlace(originalPlaces.getLast())));
			assertThatThrownBy(() -> updateService.update(owner, original.courseId(), UUID.randomUUID(), request))
				.isInstanceOfSatisfying(BusinessException.class,
					exception -> assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.BAD_REQUEST));
		}
		assertThat(readService.detail(owner, original.courseId())).isEqualTo(original);
		verifyNoInteractions(placeLookup);
	}

	private CourseDetailResponse saveCourse(long owner, UUID key) {
		var request = new CreateCourseRequest("원본 코스", List.of(
			new CreateCourseRequest.PlaceRequest(501L, List.of(
				new CreateCourseRequest.MenuRequest(null, "커피", 5000, true),
				new CreateCourseRequest.MenuRequest(null, "케이크", 7000, false))),
			new CreateCourseRequest.PlaceRequest(502L, List.of(
				new CreateCourseRequest.MenuRequest(null, "파스타", 20000, true))),
			new CreateCourseRequest.PlaceRequest(503L, List.of())));
		long courseId = saveService.save(owner, key, request, (memberId, placeId) -> Optional.of("장소 " + placeId));
		return readService.detail(owner, courseId);
	}

	private static UpdateCourseRequest replaceMiddlePlace(CourseDetailResponse original) {
		return new UpdateCourseRequest("새 장소가 있는 코스",
			List.of(copyPlace(original.places().getFirst()), newPlace(), copyPlace(original.places().getLast())));
	}

	private static UpdateCourseRequest copyCourse(CourseDetailResponse course, String name) {
		return new UpdateCourseRequest(name, course.places().stream().map(CourseUpdateServiceTests::copyPlace).toList());
	}

	private static PlaceRequest newPlace() {
		return new PlaceRequest(null, 504L, List.of(new MenuRequest(null, "주스", 6000, true)));
	}

	private static PlaceRequest copyPlace(Place place) {
		return new PlaceRequest(place.coursePlaceId(), place.placeId(),
			place.menuList().stream().map(CourseUpdateServiceTests::copyMenu).toList());
	}

	private static MenuRequest copyMenu(Menu menu) {
		return new MenuRequest(menu.menuId(), menu.menuName(), menu.price(), menu.selected());
	}
}
