package com.hsu.oneclickmeet.domain.memo.repository;

import com.hsu.oneclickmeet.domain.memo.entity.Memo;
import com.hsu.oneclickmeet.domain.memo.entity.MemoTargetType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MemoRepository extends JpaRepository<Memo, Long> {

	Optional<Memo> findByIdAndAuthorId(long id, long authorId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Memo> findForUpdateByIdAndAuthorId(long id, long authorId);

	@Query("""
			select memo.targetType as type, memo.targetId as id from Memo memo
			where memo.id = :id and memo.authorId = :authorId
			""")
	Optional<Target> findOwnedTarget(@Param("id") long id, @Param("authorId") long authorId);

	Optional<Memo> findByAuthorIdAndTargetTypeAndTargetId(long authorId, MemoTargetType targetType, long targetId);

	boolean existsByAuthorIdAndTargetTypeAndTargetId(long authorId, MemoTargetType targetType, long targetId);

	@Modifying
	@Query("update Memo memo set memo.content = :content, memo.updatedAt = :updatedAt where memo.id = :id")
	void updateContent(@Param("id") long id, @Param("content") String content, @Param("updatedAt") Instant updatedAt);

	@Query("""
			select memo from Memo memo
			where memo.authorId = :authorId
			  and (:beforeId is null or memo.createdAt < :beforeCreatedAt
			    or (memo.createdAt = :beforeCreatedAt and memo.id < :beforeId))
			order by memo.createdAt desc, memo.id desc
			""")
	List<Memo> findPage(@Param("authorId") long authorId, @Param("beforeCreatedAt") Instant beforeCreatedAt,
			@Param("beforeId") Long beforeId, Pageable limit);

	interface Target {
		MemoTargetType getType();
		long getId();
	}
}
