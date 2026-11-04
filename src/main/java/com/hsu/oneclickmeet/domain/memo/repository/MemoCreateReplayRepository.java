package com.hsu.oneclickmeet.domain.memo.repository;

import com.hsu.oneclickmeet.domain.memo.entity.MemoCreateReplay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MemoCreateReplayRepository extends JpaRepository<MemoCreateReplay, Long> {

	Optional<MemoCreateReplay> findByAuthorIdAndRequestKey(long authorId, UUID requestKey);
}
