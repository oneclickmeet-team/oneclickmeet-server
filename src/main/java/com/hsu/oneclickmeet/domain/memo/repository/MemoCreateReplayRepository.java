package com.hsu.oneclickmeet.domain.memo.repository;

import com.hsu.oneclickmeet.domain.memo.entity.MemoCreateReplay;
import com.hsu.oneclickmeet.domain.memo.entity.MemoTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MemoCreateReplayRepository extends JpaRepository<MemoCreateReplay, Long> {

	@Modifying
	@Query("""
			delete from MemoCreateReplay r where r.memoId in
			(select m.id from Memo m where m.targetType = :type and m.targetId = :id)
			""")
	void deleteForTarget(@Param("type") MemoTargetType type, @Param("id") long id);

	Optional<MemoCreateReplay> findByAuthorIdAndRequestKey(long authorId, UUID requestKey);
}
