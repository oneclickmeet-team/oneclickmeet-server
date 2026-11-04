package com.hsu.oneclickmeet.domain.course.repository;

import com.hsu.oneclickmeet.domain.course.entity.SharedCourseUpdateReplay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SharedCourseUpdateReplayRepository extends JpaRepository<SharedCourseUpdateReplay, Long> {

	Optional<SharedCourseUpdateReplay> findByMemberIdAndSharedCourseIdAndRequestKey(
			long memberId, long sharedCourseId, UUID requestKey);
}
