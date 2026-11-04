package com.hsu.oneclickmeet.domain.course.repository;

import com.hsu.oneclickmeet.domain.course.entity.CourseShareRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CourseShareRequestRepository extends JpaRepository<CourseShareRequest, Long> {

	Optional<CourseShareRequest> findByMemberIdAndSourceCourseIdAndRequestKey(long memberId, long sourceCourseId, UUID requestKey);
}
