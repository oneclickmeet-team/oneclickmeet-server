package com.hsu.oneclickmeet.domain.activity.repository;

import com.hsu.oneclickmeet.domain.activity.entity.Activity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

	/** 최신(id 큰 것)부터 읽는다. 커서가 있으면 그 id보다 작은 것만 읽는다. */
	@Query("""
			select a from Activity a
			where a.memberId = :memberId
			  and (:cursorId is null or a.id < :cursorId)
			order by a.id desc
			""")
	List<Activity> findPage(@Param("memberId") long memberId, @Param("cursorId") Long cursorId, Pageable pageable);
}
