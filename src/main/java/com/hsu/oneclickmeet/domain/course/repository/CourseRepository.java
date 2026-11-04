package com.hsu.oneclickmeet.domain.course.repository;

import com.hsu.oneclickmeet.domain.course.entity.Course;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

	@Query("select count(place) > 0 from CoursePlace place where place.id = :id")
	boolean existsPlaceById(@Param("id") long id);

	@Query("select count(menu) > 0 from CoursePlaceMenu menu where menu.id = :id")
	boolean existsMenuById(@Param("id") long id);

	@Query("""
			select course.id as courseId, course.name as courseName,
			       size(course.places) as placeCount, course.createdAt as createdAt
			from Course course
			where exists (
				select 1 from CourseLibrary library where library.course = course and library.memberId = :memberId
			)
			and (:beforeId is null or course.createdAt < :beforeCreatedAt
				or (course.createdAt = :beforeCreatedAt and course.id < :beforeId))
			order by course.createdAt desc, course.id desc
			""")
	List<Summary> findOwnedSummaries(@Param("memberId") long memberId,
			@Param("beforeCreatedAt") Instant beforeCreatedAt, @Param("beforeId") Long beforeId, Pageable limit);

	@Query("""
			select course from Course course
			where course.id = :courseId and exists (
				select 1 from CourseLibrary library where library.course = course and library.memberId = :memberId
			)
			""")
	Optional<Course> findOwnedCourse(@Param("memberId") long memberId, @Param("courseId") long courseId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select course from Course course
			where course.id = :courseId and exists (
				select 1 from CourseLibrary library where library.course = course and library.memberId = :memberId
			)
			""")
	Optional<Course> findOwnedCourseForUpdate(@Param("memberId") long memberId, @Param("courseId") long courseId);

	@Modifying(flushAutomatically = true)
	@Query("update Course course set course.updatedAt = :updatedAt where course.id = :courseId")
	void recordUpdatedAt(@Param("courseId") long courseId, @Param("updatedAt") Instant updatedAt);

	interface Summary {
		long getCourseId();
		String getCourseName();
		int getPlaceCount();
		Instant getCreatedAt();
	}
}
