package com.hsu.oneclickmeet.domain.memo.service;

import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.course.entity.Course;
import com.hsu.oneclickmeet.domain.course.entity.CourseLibrary;
import com.hsu.oneclickmeet.domain.course.entity.SharedCourse;
import com.hsu.oneclickmeet.domain.course.repository.CourseLibraryRepository;
import com.hsu.oneclickmeet.domain.course.repository.CourseRepository;
import com.hsu.oneclickmeet.domain.course.repository.SharedCourseRepository;
import com.hsu.oneclickmeet.domain.course.service.CourseDeleteService;
import com.hsu.oneclickmeet.domain.memo.dto.request.CreateMemoRequest;
import com.hsu.oneclickmeet.domain.memo.dto.response.MemoResponse;
import com.hsu.oneclickmeet.domain.memo.entity.MemoTargetType;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.persistence.MySqlTransactionLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import static com.hsu.oneclickmeet.domain.memo.entity.MemoTargetType.COURSE;
import static com.hsu.oneclickmeet.domain.memo.entity.MemoTargetType.SHARED_COURSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
class MemoServiceIntegrationTests {

	private static final long AUTHOR = 350_001L;
	private static final long PARTNER = 350_002L;
	private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");
	private static final Instant GRACE_END = NOW.plus(Duration.ofDays(30));

	@Autowired
	private MemoService service;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private CourseRepository courses;
	@Autowired
	private CourseLibraryRepository libraries;
	@Autowired
	private SharedCourseRepository sharedCourses;
	@Autowired
	private CourseDeleteService courseDeletion;
	@Autowired
	private CoupleRepository couples;
	@Autowired
	private MySqlTransactionLock locks;
	@Autowired
	private PlatformTransactionManager transactions;
	@MockitoBean(name = "coupleClock")
	private Clock clock;

	private final List<Long> courseIds = new ArrayList<>();

	@BeforeEach
	void setClock() {
		given(clock.instant()).willReturn(NOW);
	}

	@AfterEach
	void deleteFixtures() {
		jdbc.update("delete from memo_create_replays where author_id in (?, ?)", AUTHOR, PARTNER);
		jdbc.update("delete from memos where author_id in (?, ?)", AUTHOR, PARTNER);
		jdbc.update("delete from shared_course where shared_by_member_id = ?", AUTHOR);
		jdbc.update("delete from couples where first_member_id = ? and second_member_id = ?", AUTHOR, PARTNER);
		jdbc.update("delete from course_library where member_id = ?", AUTHOR);
		courses.deleteAllById(courseIds);
	}

	@Test
	void replaysTheOriginalCreationAfterEditingAndRejectsConflictingRequests() {
		long courseId = personalCourse();
		var request = new CreateMemoRequest(COURSE, courseId, "처음 내용");
		UUID key = UUID.randomUUID();

		MemoResponse first = service.create(AUTHOR, key, request);
		assertThat(first.memoId()).isPositive();
		assertThat(first.createdAt()).isNotNull();
		assertThat(first.updatedAt()).isNotNull();
		MemoResponse updated = service.update(AUTHOR, first.memoId(), "수정 내용");
		assertThat(updated.content()).isEqualTo("수정 내용");
		assertThat(updated.createdAt()).isEqualTo(first.createdAt());

		given(clock.instant()).willReturn(NOW.plus(Duration.ofHours(24)).minusNanos(1_000));
		assertThat(service.create(AUTHOR, key, request)).isEqualTo(first);
		assertThat(service.detail(AUTHOR, first.memoId())).isEqualTo(updated);
		assertCode("MEMO-0409-01", () -> service.create(AUTHOR, key,
				new CreateMemoRequest(COURSE, courseId, "다른 내용")));
		assertCode("MEMO-0409", () -> service.create(AUTHOR, UUID.randomUUID(), request));

		given(clock.instant()).willReturn(NOW.plus(Duration.ofHours(24)).plusSeconds(1));
		assertCode("MEMO-0409", () -> service.create(AUTHOR, key, request));
	}

	@Test
	void rejectsAnInProgressRetryAndAllowsItAfterTheTransactionRollsBack() {
		var request = new CreateMemoRequest(COURSE, personalCourse(), "처리 중인 메모");
		UUID key = UUID.randomUUID();
		try (var executor = Executors.newSingleThreadExecutor()) {
			new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
				assertThat(locks.tryAcquire(":memo-create:" + AUTHOR + ":" + key)).isTrue();
				var retry = executor.submit(() -> assertCode("MEMO-0409-02", () -> service.create(AUTHOR, key, request)));
				assertThat(retry).succeedsWithin(Duration.ofSeconds(5));
				transaction.setRollbackOnly();
			});
		}
		MemoResponse created = service.create(AUTHOR, key, request);
		assertThat(service.create(AUTHOR, key, request)).isEqualTo(created);
	}

	@Test
	void refreshesTheModifiedTimeWhenSavingTheSameContent() {
		MemoResponse created = create(AUTHOR, COURSE, personalCourse(), "메모 내용");
		jdbc.update("update memos set updated_at = date_sub(updated_at, interval 1 second) where id = ?",
				created.memoId());
		MemoResponse before = service.detail(AUTHOR, created.memoId());

		MemoResponse updated = service.update(AUTHOR, created.memoId(), created.content());

		assertThat(updated.createdAt()).isEqualTo(before.createdAt());
		assertThat(updated.updatedAt()).isAfter(before.updatedAt());
		assertThat(service.detail(AUTHOR, created.memoId())).isEqualTo(updated);
	}

	@Test
	void graceAllowsOnlyTheAuthorsMemosAndExpiryEndsAccessAndReplay() {
		Couple couple = couples.save(Couple.connect(AUTHOR, PARTNER, NOW));
		long sharedId = sharedCourse(couple);
		startGrace(couple);
		given(clock.instant()).willReturn(GRACE_END.minusSeconds(1));
		UUID key = UUID.randomUUID();
		var request = new CreateMemoRequest(SHARED_COURSE, sharedId, "내 메모");
		MemoResponse authorMemo = service.create(AUTHOR, key, request);
		MemoResponse partnerMemo = create(PARTNER, SHARED_COURSE, sharedId, "상대 메모");

		assertThat(service.list(AUTHOR, SHARED_COURSE, sharedId, null, 20).items()).containsExactly(authorMemo);
		assertCode("MEMO-0404", () -> service.detail(PARTNER, authorMemo.memoId()));
		assertCode("MEMO-0404", () -> service.update(PARTNER, authorMemo.memoId(), "권한 없음"));
		assertCode("MEMO-0404", () -> service.delete(PARTNER, authorMemo.memoId()));
		assertThat(service.update(AUTHOR, authorMemo.memoId(), "유예 중 수정").content()).isEqualTo("유예 중 수정");
		service.delete(PARTNER, partnerMemo.memoId());
		assertCode("MEMO-0404", () -> service.detail(PARTNER, partnerMemo.memoId()));
		assertThat(service.create(AUTHOR, key, request)).isEqualTo(authorMemo);

		given(clock.instant()).willReturn(GRACE_END);
		assertCode("MEMO-0404", () -> service.detail(AUTHOR, authorMemo.memoId()));
		assertCode("MEMO-0404", () -> service.update(AUTHOR, authorMemo.memoId(), "유예 종료 후 수정"));
		assertCode("MEMO-0404", () -> service.delete(AUTHOR, authorMemo.memoId()));
		assertCode("COURSE-0404", () -> service.create(AUTHOR, key, request));
		assertCode("COURSE-0404", () -> service.list(AUTHOR, SHARED_COURSE, sharedId, null, 20));
	}

	@Test
	void paginatesPastInaccessibleTargetsAndUsesMemoIdToBreakTimeTies() {
		MemoResponse older = create(AUTHOR, COURSE, personalCourse(), "이전 개인 코스");
		MemoResponse newer = create(AUTHOR, COURSE, personalCourse(), "최근 개인 코스");
		Couple couple = couples.save(Couple.connect(AUTHOR, PARTNER, NOW));
		MemoResponse shared = create(AUTHOR, SHARED_COURSE, sharedCourse(couple), "공동 코스");
		long inaccessibleId = personalCourse();
		create(AUTHOR, COURSE, inaccessibleId, "접근 권한을 잃은 개인 코스");
		courseDeletion.delete(AUTHOR, inaccessibleId);
		jdbc.update("update memos set created_at = ? where author_id = ?", Timestamp.from(NOW), AUTHOR);
		startGrace(couple);

		assertThat(service.list(AUTHOR, null, null, null, 1).items())
				.extracting(MemoResponse::memoId).containsExactly(shared.memoId());
		given(clock.instant()).willReturn(GRACE_END);

		var first = service.list(AUTHOR, null, null, null, 1);
		assertThat(first.items()).extracting(MemoResponse::memoId).containsExactly(newer.memoId());
		assertThat(first.hasNext()).isTrue();
		assertThat(first.nextCursor()).isNotBlank();
		var second = service.list(AUTHOR, null, null, first.nextCursor(), 1);
		assertThat(second.items()).extracting(MemoResponse::memoId).containsExactly(older.memoId());
		assertThat(second.hasNext()).isFalse();
		assertThat(second.nextCursor()).isNull();
	}

	private MemoResponse create(long authorId, MemoTargetType type, long targetId, String content) {
		return service.create(authorId, UUID.randomUUID(), new CreateMemoRequest(type, targetId, content));
	}

	private long personalCourse() {
		Course course = saveCourse("개인 코스");
		libraries.save(CourseLibrary.create(course, AUTHOR));
		return course.getId();
	}

	private long sharedCourse(Couple couple) {
		long sourceId = personalCourse();
		Course copy = saveCourse("공동 코스 사본");
		return sharedCourses.save(SharedCourse.create(copy, couple, sourceId, AUTHOR)).getId();
	}

	private Course saveCourse(String name) {
		Course course = courses.save(Course.create(name));
		courseIds.add(course.getId());
		return course;
	}

	private void startGrace(Couple couple) {
		couple.disconnect(NOW);
		couples.saveAndFlush(couple);
	}

	private void assertCode(String code, Runnable request) {
		assertThatExceptionOfType(BusinessException.class).isThrownBy(request::run)
				.satisfies(exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo(code));
	}
}
