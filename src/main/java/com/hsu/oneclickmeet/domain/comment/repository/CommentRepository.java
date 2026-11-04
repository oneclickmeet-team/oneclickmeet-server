package com.hsu.oneclickmeet.domain.comment.repository;

import com.hsu.oneclickmeet.domain.comment.entity.Comment;
import com.hsu.oneclickmeet.domain.comment.entity.CommentTargetType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	@Query("""
			select c from Comment c
			where c.targetType = :targetType and c.targetId = :targetId
			  and (:afterId is null or c.createdAt > :afterCreatedAt
			       or (c.createdAt = :afterCreatedAt and c.id > :afterId))
			order by c.createdAt, c.id
			""")
	List<Comment> findPage(@Param("targetType") CommentTargetType targetType, @Param("targetId") long targetId,
			@Param("afterCreatedAt") Instant afterCreatedAt, @Param("afterId") Long afterId, Pageable limit);

	@Query("select c.targetType as type, c.targetId as id from Comment c where c.id = :commentId")
	Optional<Target> findTarget(@Param("commentId") long commentId);

	interface Target {
		CommentTargetType getType();
		long getId();
	}
}
