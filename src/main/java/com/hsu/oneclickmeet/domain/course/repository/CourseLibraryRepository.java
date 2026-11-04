package com.hsu.oneclickmeet.domain.course.repository;

import com.hsu.oneclickmeet.domain.course.entity.CourseLibrary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface CourseLibraryRepository extends JpaRepository<CourseLibrary, Long> {
	boolean existsByMemberIdAndCourse_Id(long memberId, long courseId);

	@Query("""
			select library.course.id from CourseLibrary library
			where library.memberId = :memberId and library.course.id in :courseIds
			""")
	Set<Long> findOwnedCourseIds(@Param("memberId") long memberId, @Param("courseIds") Set<Long> courseIds);

	@Modifying
	@Query("delete from CourseLibrary library where library.memberId = :memberId and library.course.id = :courseId")
	int deleteOwnedLinks(@Param("memberId") long memberId, @Param("courseId") long courseId);
}
