package com.hsu.oneclickmeet.domain.activity.service;

import com.hsu.oneclickmeet.domain.activity.dto.response.ActivityListResponse;
import com.hsu.oneclickmeet.domain.activity.entity.ActivityType;
import com.hsu.oneclickmeet.domain.activity.repository.ActivityRepository;
import com.hsu.oneclickmeet.domain.notification.client.PushSender;
import com.hsu.oneclickmeet.domain.notification.dto.response.NotificationListResponse;
import com.hsu.oneclickmeet.domain.notification.service.NotificationService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ActivityRecorderTests {

	private static final long ACTOR = 840_001L;
	private static final long PARTNER = 840_002L;

	@Autowired
	private ActivityService activityService;
	@Autowired
	private NotificationService notificationService;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private ActivityRepository activityRepository;

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from activities where member_id in (?, ?)", ACTOR, PARTNER);
		jdbc.update("delete from notifications where member_id in (?, ?)", ACTOR, PARTNER);
	}

	private ActivityRecorder recorder(PushSender sender) {
		return new ActivityRecorder(activityRepository, notificationService, sender, transactionManager);
	}

	private static ActivityEvent shared(Long recipient) {
		return new ActivityEvent(ACTOR, recipient, ActivityType.COURSE_SHARED, "COURSE", 5L, "코스 '성신여대 데이트'를 공유했어요");
	}

	@Test
	@DisplayName("상대가 있으면 내 기록과 상대 알림을 함께 남기고 푸시를 보낸다")
	void recordsActivityAndNotifiesPartner() {
		List<String> pushed = new ArrayList<>();

		recorder((memberId, title, body) -> pushed.add(memberId + "|" + title)).record(shared(PARTNER));

		ActivityListResponse mine = activityService.list(ACTOR, null, 20);
		NotificationListResponse theirs = notificationService.list(PARTNER, null, 20);
		assertThat(mine.items()).singleElement().satisfies(item -> {
			assertThat(item.type()).isEqualTo(ActivityType.COURSE_SHARED);
			assertThat(item.targetId()).isEqualTo(5L);
		});
		assertThat(theirs.items()).singleElement().satisfies(item -> {
			assertThat(item.read()).isFalse();
			assertThat(item.title()).isEqualTo("코스가 공유되었어요");
		});
		assertThat(theirs.unreadCount()).isEqualTo(1);
		assertThat(pushed).containsExactly(PARTNER + "|코스가 공유되었어요");
		assertThat(notificationService.list(ACTOR, null, 20).items()).isEmpty();
	}

	@Test
	@DisplayName("받는 사람이 없거나 본인이면 기록만 남기고 알림·푸시는 없다")
	void onlyRecordsWhenNoOtherRecipient() {
		List<String> pushed = new ArrayList<>();
		ActivityRecorder recorder = recorder((memberId, title, body) -> pushed.add(title));

		recorder.record(shared(null));
		recorder.record(shared(ACTOR));

		assertThat(activityService.list(ACTOR, null, 20).items()).hasSize(2);
		assertThat(notificationService.list(ACTOR, null, 20).items()).isEmpty();
		assertThat(pushed).isEmpty();
	}

	@Test
	@DisplayName("푸시가 실패해도 기록과 알림은 남는다")
	void keepsRecordsWhenPushFails() {
		recorder((memberId, title, body) -> {
			throw new IllegalStateException("push down");
		}).record(shared(PARTNER));

		assertThat(activityService.list(ACTOR, null, 20).items()).hasSize(1);
		assertThat(notificationService.list(PARTNER, null, 20).items()).hasSize(1);
	}

	@Test
	@DisplayName("값이 잘못되면 기록도 알림도 남기지 않는다")
	void rollsBackEverythingWhenInvalid() {
		ActivityEvent invalid = new ActivityEvent(ACTOR, PARTNER, ActivityType.COURSE_SHARED, "COURSE", 5L, " ");

		assertThatThrownBy(() -> recorder((memberId, title, body) -> { }).record(invalid))
				.isInstanceOf(IllegalArgumentException.class);

		assertThat(activityService.list(ACTOR, null, 20).items()).isEmpty();
		assertThat(notificationService.list(PARTNER, null, 20).items()).isEmpty();
	}

	@Test
	@DisplayName("활동 목록은 최신순 커서로 이어 읽고 본인 기록만 보인다")
	void listsActivitiesWithCursor() {
		ActivityRecorder recorder = recorder((memberId, title, body) -> { });
		for (int i = 1; i <= 3; i++) {
			recorder.record(new ActivityEvent(ACTOR, null, ActivityType.COURSE_DELETED, "COURSE", i, "삭제 " + i));
		}
		recorder.record(new ActivityEvent(PARTNER, null, ActivityType.COURSE_DELETED, "COURSE", 9, "다른 사람"));

		ActivityListResponse first = activityService.list(ACTOR, null, 2);
		ActivityListResponse second = activityService.list(ACTOR, first.nextCursor(), 2);

		assertThat(first.items()).extracting(item -> item.targetId()).containsExactly(3L, 2L);
		assertThat(first.hasNext()).isTrue();
		assertThat(second.items()).extracting(item -> item.targetId()).containsExactly(1L);
		assertThat(second.hasNext()).isFalse();
		assertThat(second.nextCursor()).isNull();
		assertThatThrownBy(() -> activityService.list(PARTNER, first.nextCursor(), 2))
				.isInstanceOf(BusinessException.class);
	}
}
