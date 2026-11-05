package com.hsu.oneclickmeet.domain.activity;

import com.hsu.oneclickmeet.domain.activity.entity.Activity;
import com.hsu.oneclickmeet.domain.activity.entity.ActivityType;
import com.hsu.oneclickmeet.domain.notification.entity.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ActivityEntityTests {

	@Test
	@DisplayName("활동과 알림은 값을 다듬어서 만든다")
	void createsTrimmed() {
		Activity activity = Activity.create(1L, ActivityType.COURSE_SHARED, " COURSE ", 5L, " 요약 ");
		Notification notification = Notification.create(1L, ActivityType.COURSE_SHARED, " 제목 ", " 내용 ", "COURSE", 5L);

		assertThat(activity.getTargetType()).isEqualTo("COURSE");
		assertThat(activity.getSummary()).isEqualTo("요약");
		assertThat(notification.getTitle()).isEqualTo("제목");
		assertThat(notification.isRead()).isFalse();
	}

	@Test
	@DisplayName("잘못된 값은 거절한다")
	void rejectsInvalidValues() {
		assertThatIllegalArgumentException().isThrownBy(() -> Activity.create(0L, ActivityType.COURSE_SHARED, "COURSE", 5L, "요약"));
		assertThatIllegalArgumentException().isThrownBy(() -> Activity.create(1L, null, "COURSE", 5L, "요약"));
		assertThatIllegalArgumentException().isThrownBy(() -> Activity.create(1L, ActivityType.COURSE_SHARED, " ", 5L, "요약"));
		assertThatIllegalArgumentException().isThrownBy(() -> Activity.create(1L, ActivityType.COURSE_SHARED, "COURSE", 0L, "요약"));
		assertThatIllegalArgumentException().isThrownBy(() -> Activity.create(1L, ActivityType.COURSE_SHARED, "COURSE", 5L, "가".repeat(201)));
		assertThatIllegalArgumentException().isThrownBy(() -> Notification.create(1L, ActivityType.COURSE_SHARED, "", "내용", "COURSE", 5L));
		assertThatIllegalArgumentException().isThrownBy(() -> Notification.create(1L, ActivityType.COURSE_SHARED, "제목", "가".repeat(301), "COURSE", 5L));
	}
}
