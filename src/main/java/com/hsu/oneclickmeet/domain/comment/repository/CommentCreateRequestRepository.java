package com.hsu.oneclickmeet.domain.comment.repository;

import com.hsu.oneclickmeet.domain.comment.entity.CommentCreateRequestReplay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommentCreateRequestRepository extends JpaRepository<CommentCreateRequestReplay, Long> {

	Optional<CommentCreateRequestReplay> findByMemberIdAndRequestKey(long memberId, UUID requestKey);
}
