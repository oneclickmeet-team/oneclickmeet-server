package com.hsu.oneclickmeet.domain.notification.repository;

import com.hsu.oneclickmeet.domain.notification.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

	Optional<DeviceToken> findByToken(String token);

	/** 푸시를 보낼 때 쓸 회원의 기기 토큰(최근에 등록·갱신한 것부터). */
	List<DeviceToken> findByMemberIdOrderByUpdatedAtDescIdDesc(long memberId);

	long countByMemberId(long memberId);

	/** 본인의 토큰만 지운다. 처리된 행 수가 0이면 없거나 다른 회원의 토큰이다. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from DeviceToken d where d.token = :token and d.memberId = :memberId")
	int deleteByTokenAndMemberId(@Param("token") String token, @Param("memberId") long memberId);
}
