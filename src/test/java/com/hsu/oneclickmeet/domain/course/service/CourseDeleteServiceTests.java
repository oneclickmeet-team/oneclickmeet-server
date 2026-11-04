package com.hsu.oneclickmeet.domain.course.service;

import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.MenuRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.PlaceRequest;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseListResponse.Item;
import com.hsu.oneclickmeet.domain.course.entity.Course;
import com.hsu.oneclickmeet.domain.course.exception.CourseErrorCode;
import com.hsu.oneclickmeet.domain.course.repository.CourseRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=2")
@ActiveProfiles("test")
class CourseDeleteServiceTests {

	@Autowired
	private CourseDeleteService deleteService;

	@Autowired
	private CourseSaveService saveService;

	@Autowired
	private CourseReadService readService;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void closesPersonalAccessAndPreservesSharedAndOtherPersonalCopies() {
		long ownerId = 401L;
		long otherOwnerId = 402L;
		UUID originalSaveKey = UUID.randomUUID();
		long courseId = save(ownerId, originalSaveKey, "삭제할 개인 코스");
		long otherPersonalCopyId = save(otherOwnerId, UUID.randomUUID(), "다른 회원의 개인 사본");
		long sharedCopyId = courseRepository.save(Course.create("독립 공동 사본")).getId();
		jdbc.update("""
				insert into shared_course (shared_course_id, course_id, created_at, updated_at)
				values (91001, ?, current_timestamp, current_timestamp)
				""", sharedCopyId);
		jdbc.update("update course_place set place_memo = ? where course_id = ? and visit_order = 1",
				"예약 내용", courseId);

		deleteService.delete(ownerId, courseId);

		assertThat(readService.list(ownerId, null, 20).items()).extracting(Item::courseId).doesNotContain(courseId);
		assertNotFound(() -> readService.detail(ownerId, courseId));
		assertNotFound(() -> deleteService.delete(ownerId, courseId));
		assertNotFound(() -> save(ownerId, originalSaveKey, "삭제할 개인 코스"));
		assertThat(readService.detail(otherOwnerId, otherPersonalCopyId).selectedTotalPrice()).isEqualTo(25_000L);
		assertThat(jdbc.queryForObject("select course_id from shared_course where shared_course_id = 91001", Long.class))
				.isEqualTo(sharedCopyId);
		assertThat(jdbc.queryForObject("select place_memo from course_place where course_id = ? and visit_order = 1",
				String.class, courseId)).isEqualTo("예약 내용");
	}

	@Test
	void rejectsOtherMembersAndMissingCoursesWithTheSameError() {
		long ownerId = 403L;
		long otherMemberId = 404L;
		long courseId = save(ownerId, UUID.randomUUID(), "유지할 개인 코스");

		assertNotFound(() -> deleteService.delete(otherMemberId, courseId));
		assertNotFound(() -> deleteService.delete(otherMemberId, Long.MAX_VALUE));
		assertThat(readService.detail(ownerId, courseId).courseId()).isEqualTo(courseId);
	}

	private static void assertNotFound(Runnable operation) {
		assertThatThrownBy(operation::run)
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.getErrorCode()).isEqualTo(CourseErrorCode.NOT_FOUND));
	}

	private long save(long memberId, UUID requestKey, String courseName) {
		var request = new CreateCourseRequest(courseName, List.of(
				new PlaceRequest(503L, List.of(new MenuRequest(null, "파스타", 25_000, true))),
				new PlaceRequest(501L, List.of()),
				new PlaceRequest(502L, List.of())));
		return saveService.save(memberId, requestKey, request, (owner, placeId) -> Optional.of("장소 " + placeId));
	}
}
