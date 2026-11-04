package com.hsu.oneclickmeet.domain.course.repository;

import com.hsu.oneclickmeet.domain.course.entity.CourseSaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CourseSaveRequestRepository extends JpaRepository<CourseSaveRequest, Long> {
	Optional<CourseSaveRequest> findByMemberIdAndRequestKey(long memberId, UUID requestKey);
}
