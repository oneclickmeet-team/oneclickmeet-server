package com.hsu.oneclickmeet.domain.notification.service;

import com.hsu.oneclickmeet.domain.activity.entity.ActivityType;
import com.hsu.oneclickmeet.domain.notification.dto.response.NotificationListResponse;
import com.hsu.oneclickmeet.domain.notification.dto.response.NotificationResponse;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceTests {

	private static final long MEMBER = 850_001L;
	private static final long OTHER = 850_002L;

	@Autowired
	private NotificationService service;
	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from notifications where member_id in (?, ?)", MEMBER, OTHER);
	}

	private long create(long memberId, int n) {
		service.create(memberId, ActivityType.COURSE_SHARED, "제목 " + n, "내용 " + n, "COURSE", n);
		return jdbc.queryForObject("select max(id) from notifications where member_id = ?", Long.class, memberId);
	}

	@Test
	@DisplayName("목록은 최신순이며 읽지 않은 개수를 함께 돌려준다")
	void listsNewestFirstWithUnreadCount() {
		create(MEMBER, 1);
		create(MEMBER, 2);
		create(OTHER, 3);

		NotificationListResponse page = service.list(MEMBER, null, 20);

		assertThat(page.items()).extracting(NotificationResponse::targetId).containsExactly(2L, 1L);
		assertThat(page.unreadCount()).isEqualTo(2);
		assertThat(page.hasNext()).isFalse();
	}

	@Test
	@DisplayName("커서로 이어 읽고 다른 회원의 커서는 400이다")
	void pagesWithCursor() {
		for (int i = 1; i <= 3; i++) {
			create(MEMBER, i);
		}

		NotificationListResponse first = service.list(MEMBER, null, 2);
		NotificationListResponse second = service.list(MEMBER, first.nextCursor(), 2);

		assertThat(first.hasNext()).isTrue();
		assertThat(second.items()).extracting(NotificationResponse::targetId).containsExactly(1L);
		assertThat(second.hasNext()).isFalse();
		assertThatThrownBy(() -> service.list(OTHER, first.nextCursor(), 2)).isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> service.list(MEMBER, "garbage", 2)).isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("읽음 처리하면 읽은 상태가 되고 읽지 않은 개수가 줄며, 다시 해도 안전하다")
	void marksReadIdempotently() {
		long id = create(MEMBER, 1);
		create(MEMBER, 2);

		NotificationResponse first = service.markRead(MEMBER, id);
		java.time.Instant readAt = jdbc.queryForObject("select read_at from notifications where id = ?",
				java.sql.Timestamp.class, id).toInstant();
		NotificationResponse again = service.markRead(MEMBER, id);

		assertThat(first.read()).isTrue();
		assertThat(again.read()).isTrue();
		assertThat(jdbc.queryForObject("select read_at from notifications where id = ?", java.sql.Timestamp.class, id)
				.toInstant()).isEqualTo(readAt);
		assertThat(service.list(MEMBER, null, 20).unreadCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("없거나 다른 회원의 알림은 읽음 처리할 수 없고 404이며 상태도 바뀔지 않는다")
	void hidesOtherMembersNotifications() {
		long id = create(MEMBER, 1);

		assertThatThrownBy(() -> service.markRead(OTHER, id)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode().getCode()).isEqualTo("NOTIFICATION-0404"));
		assertThatThrownBy(() -> service.markRead(MEMBER, id + 1_000_000)).isInstanceOf(BusinessException.class);

		assertThat(service.list(MEMBER, null, 20).unreadCount()).isEqualTo(1);
	}
}
