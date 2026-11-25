package com.hsu.oneclickmeet.domain.comment.repository;

import com.hsu.oneclickmeet.domain.comment.entity.CommentCreateRequestReplay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CommentCreateRequestRepository extends JpaRepository<CommentCreateRequestReplay, Long> {

	@Modifying
	@Query(value = """
			delete from comment_create_requests
			where json_unquote(json_extract(response_json, '$.targetType')) = 'SHARED_PLACE'
			  and json_unquote(json_extract(response_json, '$.targetId')) = cast(:id as char)
			""", nativeQuery = true)
	void deleteForSharedPlace(@Param("id") long id);

	Optional<CommentCreateRequestReplay> findByMemberIdAndRequestKey(long memberId, UUID requestKey);
}
