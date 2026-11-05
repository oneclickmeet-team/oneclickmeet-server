package com.hsu.oneclickmeet.domain.activity.service;

import com.hsu.oneclickmeet.domain.activity.entity.Activity;
import com.hsu.oneclickmeet.domain.activity.entity.ActivityType;
import com.hsu.oneclickmeet.domain.activity.repository.ActivityRepository;
import com.hsu.oneclickmeet.domain.notification.client.PushSender;
import com.hsu.oneclickmeet.domain.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 활동 기록과 상대방 알림을 한 번에 남긴다. 기록과 알림은 한 트랜잭션으로 저장하고,
 * 푸시는 저장이 끝난 뒤 보내며 실패해도 기록·알림에는 영향을 주지 않는다.
 */
@Component
public class ActivityRecorder {

	private static final Logger log = LoggerFactory.getLogger(ActivityRecorder.class);

	private final ActivityRepository activityRepository;
	private final NotificationService notificationService;
	private final PushSender pushSender;
	private final TransactionTemplate transaction;

	public ActivityRecorder(ActivityRepository activityRepository, NotificationService notificationService,
			PushSender pushSender, PlatformTransactionManager transactionManager) {
		this.activityRepository = activityRepository;
		this.notificationService = notificationService;
		this.pushSender = pushSender;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	public void record(ActivityEvent event) {
		boolean notifies = event.recipientMemberId() != null && event.recipientMemberId() != event.actorMemberId();
		String title = titleOf(event.type());
		transaction.executeWithoutResult(status -> {
			activityRepository.save(Activity.create(event.actorMemberId(), event.type(), event.targetType(),
					event.targetId(), event.summary()));
			if (notifies) {
				notificationService.create(event.recipientMemberId(), event.type(), title, event.summary(),
						event.targetType(), event.targetId());
			}
		});
		if (notifies) {
			try {
				pushSender.send(event.recipientMemberId(), title, event.summary());
			} catch (RuntimeException exception) {
				// 푸시는 부가 기능이다. 내용(제목·본문)은 사용자 데이터이므로 로그에 남기지 않는다.
				log.warn("푸시 전송에 실패했습니다. type={}", event.type());
			}
		}
	}

	private static String titleOf(ActivityType type) {
		return switch (type) {
			case COURSE_SHARED -> "코스가 공유되었어요";
			case COURSE_SHARE_CANCELED -> "코스 공유가 취소되었어요";
			case COURSE_DELETED -> "코스가 삭제되었어요";
			case AMOUNT_CHANGED -> "금액 항목이 바뀌었어요";
		};
	}
}
