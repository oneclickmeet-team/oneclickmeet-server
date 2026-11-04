package com.hsu.oneclickmeet.domain.course.repository;

import com.hsu.oneclickmeet.domain.course.entity.SharedCourseCancellationCopy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedCourseCancellationCopyRepository extends JpaRepository<SharedCourseCancellationCopy, Long> {

	boolean existsBySharedCourseId(long sharedCourseId);

	boolean existsBySharedCourseIdAndMemberId(long sharedCourseId, long memberId);
}
