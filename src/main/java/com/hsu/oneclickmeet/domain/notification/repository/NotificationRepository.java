package com.hsu.oneclickmeet.domain.notification.repository;

import com.hsu.oneclickmeet.domain.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	Optional<Notification> findByIdAndMemberId(long id, long memberId);

	long countByMemberIdAndReadAtIsNull(long memberId);

	@Query("""
			select n from Notification n
			where n.memberId = :memberId
			  and (:cursorId is null or n.id < :cursorId)
			order by n.id desc
			""")
	List<Notification> findPage(@Param("memberId") long memberId, @Param("cursorId") Long cursorId,
			Pageable pageable);

	/** 아직 읽지 않은 경우에만 읽은 시각을 기록한다. 이미 읽은 알림의 시각은 바꾸지 않는다. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Notification n set n.readAt = :readAt where n.id = :id and n.memberId = :memberId and n.readAt is null")
	int markRead(@Param("id") long id, @Param("memberId") long memberId, @Param("readAt") Instant readAt);
}
