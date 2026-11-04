package com.hsu.oneclickmeet.domain.course.repository;

import com.hsu.oneclickmeet.domain.course.entity.CourseLibrary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseLibraryRepository extends JpaRepository<CourseLibrary, Long> {
	boolean existsByMemberIdAndCourse_Id(long memberId, long courseId);

	@Modifying
	@Query("delete from CourseLibrary library where library.memberId = :memberId and library.course.id = :courseId")
	int deleteOwnedLinks(@Param("memberId") long memberId, @Param("courseId") long courseId);
}
