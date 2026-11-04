package com.hsu.oneclickmeet.domain.course.repository;

import com.hsu.oneclickmeet.domain.course.entity.CourseUpdateRequestReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CourseUpdateRequestRepository extends JpaRepository<CourseUpdateRequestReceipt, Long> {
	Optional<CourseUpdateRequestReceipt> findByMemberIdAndCourseIdAndRequestKey(
			long memberId, long courseId, UUID requestKey);
}
