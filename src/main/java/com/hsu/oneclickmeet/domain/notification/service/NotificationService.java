package com.hsu.oneclickmeet.domain.notification.service;

import com.hsu.oneclickmeet.domain.activity.entity.ActivityType;
import com.hsu.oneclickmeet.domain.notification.dto.response.NotificationListResponse;
import com.hsu.oneclickmeet.domain.notification.dto.response.NotificationResponse;
import com.hsu.oneclickmeet.domain.notification.entity.Notification;
import com.hsu.oneclickmeet.domain.notification.exception.NotificationErrorCode;
import com.hsu.oneclickmeet.domain.notification.repository.NotificationRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/** 알림 저장·조회·읽음 처리. 조회와 읽음 처리는 회원 본인의 알림만 대상으로 한다. */
@Service
public class NotificationService {

	private final NotificationRepository repository;
	private final TransactionTemplate transaction;

	public NotificationService(NotificationRepository repository, PlatformTransactionManager transactionManager) {
		this.repository = repository;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/** 다른 트랜잭션 안에서 호출되면 그 트랜잭션에 참여한다. */
	public void create(long memberId, ActivityType type, String title, String body, String targetType,
			long targetId) {
		transaction.executeWithoutResult(status -> repository
				.save(Notification.create(memberId, type, title, body, targetType, targetId)));
	}

	public NotificationListResponse list(long memberId, String cursor, int size) {
		Long cursorId = NotificationCursor.decode(cursor, memberId);
		return transaction.execute(status -> {
			List<Notification> found = repository.findPage(memberId, cursorId, PageRequest.ofSize(size + 1));
			boolean hasNext = found.size() > size;
			List<Notification> page = hasNext ? found.subList(0, size) : found;
			String nextCursor = hasNext
					? NotificationCursor.encode(memberId, page.get(page.size() - 1).getId())
					: null;
			return new NotificationListResponse(page.stream().map(NotificationResponse::from).toList(),
					repository.countByMemberIdAndReadAtIsNull(memberId), nextCursor, hasNext);
		});
	}

	/** 읽음 처리. 이미 읽은 알림이면 그대로 두고 성공으로 본다. 없거나 다른 회원의 알림은 404다. */
	public NotificationResponse markRead(long memberId, long notificationId) {
		return transaction.execute(status -> {
			repository.markRead(notificationId, memberId, Instant.now());
			return repository.findByIdAndMemberId(notificationId, memberId)
					.map(NotificationResponse::from)
					.orElseThrow(() -> new BusinessException(NotificationErrorCode.NOT_FOUND));
		});
	}
}
