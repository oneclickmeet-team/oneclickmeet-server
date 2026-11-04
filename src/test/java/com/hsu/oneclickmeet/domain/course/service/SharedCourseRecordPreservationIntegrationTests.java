package com.hsu.oneclickmeet.domain.course.service;

import com.hsu.oneclickmeet.domain.comment.dto.response.CommentResponse;
import com.hsu.oneclickmeet.domain.comment.entity.CommentTargetType;
import com.hsu.oneclickmeet.domain.comment.service.CommentArchiveService;
import com.hsu.oneclickmeet.domain.comment.service.CommentService;
import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.course.entity.Course;
import com.hsu.oneclickmeet.domain.course.entity.CourseLibrary;
import com.hsu.oneclickmeet.domain.course.entity.SharedCourse;
import com.hsu.oneclickmeet.domain.course.repository.CourseLibraryRepository;
import com.hsu.oneclickmeet.domain.course.repository.CourseRepository;
import com.hsu.oneclickmeet.domain.course.repository.SharedCourseRepository;
import com.hsu.oneclickmeet.domain.memo.dto.request.CreateMemoRequest;
import com.hsu.oneclickmeet.domain.memo.dto.response.MemoResponse;
import com.hsu.oneclickmeet.domain.memo.entity.MemoTargetType;
import com.hsu.oneclickmeet.domain.memo.service.MemoService;
import com.hsu.oneclickmeet.domain.memo.service.MemoTransferService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
class SharedCourseRecordPreservationIntegrationTests {

	private static final long OWNER = 370_001L;
	private static final long PARTNER = 370_002L;
	private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
	private static final Instant ARCHIVED_AT = NOW.plusSeconds(2);

	@Autowired
	private CourseRepository courses;
	@Autowired
	private CourseLibraryRepository libraries;
	@Autowired
	private SharedCourseRepository sharedCourses;
	@Autowired
	private CoupleRepository couples;
	@Autowired
	private MemoService memos;
	@Autowired
	private CommentService comments;
	@Autowired
	private MemoTransferService memoTransfer;
	@Autowired
	private CommentArchiveService commentArchive;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private JdbcTemplate jdbc;
	@MockitoBean(name = "coupleClock")
	private Clock clock;

	private final List<Long> courseIds = new ArrayList<>();
	private long coupleId;
	private long sourceCourseId;
	private long sharedCourseId;
	private long ownerCopyId;
	private long partnerCopyId;

	@BeforeEach
	void prepareCourses() {
		given(clock.instant()).willReturn(NOW);
		jdbc.execute("create table if not exists members (id bigint primary key, name varchar(100) not null)");
		jdbc.update("insert into members (id, name) values (?, '공유자'), (?, '상대방')", OWNER, PARTNER);
		Couple couple = couples.save(Couple.connect(OWNER, PARTNER, NOW));
		coupleId = couple.getId();
		sourceCourseId = savePersonalCourse(OWNER, "기존 개인 코스");
		sharedCourseId = sharedCourses.save(SharedCourse.create(saveCourse("공동 코스"), couple, sourceCourseId, OWNER))
				.getId();
		ownerCopyId = savePersonalCourse(OWNER, "공유자 개인 사본");
		partnerCopyId = savePersonalCourse(PARTNER, "상대방 개인 사본");
	}

	@AfterEach
	void removeFixtures() {
		jdbc.update("delete from comment_create_requests where member_id in (?, ?)", OWNER, PARTNER);
		jdbc.update("delete from comments where author_member_id in (?, ?)", OWNER, PARTNER);
		jdbc.update("delete from memo_create_replays where author_id in (?, ?)", OWNER, PARTNER);
		jdbc.update("delete from memos where author_id in (?, ?)", OWNER, PARTNER);
		jdbc.update("delete from shared_course where shared_by_member_id = ?", OWNER);
		jdbc.update("delete from course_library where member_id in (?, ?)", OWNER, PARTNER);
		jdbc.update("delete from couples where first_member_id = ? and second_member_id = ?", OWNER, PARTNER);
		courses.deleteAllById(courseIds);
		jdbc.update("delete from members where id in (?, ?)", OWNER, PARTNER);
	}

	@Test
	void preservesPrivateMemosAndArchivesBothAuthorsCommentsOnEachCopyWithoutDuplicates() {
		MemoResponse original = createMemo(OWNER, MemoTargetType.COURSE, sourceCourseId, "기존 개인 메모");
		MemoResponse ownerMemo = createMemo(OWNER, MemoTargetType.SHARED_COURSE, sharedCourseId, "공유자 메모");
		MemoResponse partnerMemo = createMemo(PARTNER, MemoTargetType.SHARED_COURSE, sharedCourseId, "상대방 메모");
		CommentResponse ownerComment = comments.create(OWNER, sharedCourseId, "공유자 첨언", UUID.randomUUID());
		given(clock.instant()).willReturn(NOW.plusSeconds(1));
		ownerComment = comments.update(OWNER, ownerComment.commentId(), "수정한 공유자 첨언");
		CommentResponse partnerComment = comments.create(PARTNER, sharedCourseId, "상대방 첨언", UUID.randomUUID());

		preserve(ARCHIVED_AT, false);

		assertThat(personalMemos(OWNER, sourceCourseId)).containsExactly(original);
		assertThat(personalMemos(OWNER, ownerCopyId)).containsExactly(onCopy(ownerMemo, ownerCopyId));
		assertThat(personalMemos(PARTNER, partnerCopyId)).containsExactly(onCopy(partnerMemo, partnerCopyId));
		assertThat(memos.list(OWNER, MemoTargetType.SHARED_COURSE, sharedCourseId, null, 20).items()).isEmpty();
		assertCode("COURSE-0404", () -> personalMemos(OWNER, partnerCopyId));
		assertCode("COURSE-0404", () -> personalComments(PARTNER, ownerCopyId));
		assertThat(personalComments(OWNER, sourceCourseId)).isEmpty();

		List<CommentResponse> ownerArchives = personalComments(OWNER, ownerCopyId);
		List<CommentResponse> partnerArchives = personalComments(PARTNER, partnerCopyId);
		assertThat(ownerArchives).hasSize(2);
		assertThat(partnerArchives).hasSize(2);
		assertArchive(ownerArchives.get(0), ownerComment, ownerCopyId, true);
		assertArchive(ownerArchives.get(1), partnerComment, ownerCopyId, false);
		assertArchive(partnerArchives.get(0), ownerComment, partnerCopyId, false);
		assertArchive(partnerArchives.get(1), partnerComment, partnerCopyId, true);
		assertThat(List.of(ownerComment.commentId(), partnerComment.commentId(), ownerArchives.get(0).commentId(),
				ownerArchives.get(1).commentId(), partnerArchives.get(0).commentId(), partnerArchives.get(1).commentId()))
				.doesNotHaveDuplicates();
		assertCode("COMMENT-0409-03", () -> comments.update(OWNER, ownerArchives.get(0).commentId(), "수정"));
		assertCode("COMMENT-0409-03", () -> comments.delete(PARTNER, partnerArchives.get(1).commentId()));

		preserve(ARCHIVED_AT.plusSeconds(1), false);

		assertThat(personalComments(OWNER, ownerCopyId)).containsExactlyElementsOf(ownerArchives);
		assertThat(personalComments(PARTNER, partnerCopyId)).containsExactlyElementsOf(partnerArchives);
		assertThat(personalMemos(OWNER, ownerCopyId)).containsExactly(onCopy(ownerMemo, ownerCopyId));
		assertThat(personalMemos(PARTNER, partnerCopyId)).containsExactly(onCopy(partnerMemo, partnerCopyId));
		memos.delete(OWNER, ownerMemo.memoId());
		preserve(ARCHIVED_AT.plusSeconds(2), false);
		assertThat(personalMemos(OWNER, ownerCopyId)).isEmpty();
	}

	@Test
	void rollsBackRecordPreservationWithTheCallingTransaction() {
		MemoResponse memo = createMemo(OWNER, MemoTargetType.SHARED_COURSE, sharedCourseId, "보존할 메모");
		CommentResponse comment = comments.create(PARTNER, sharedCourseId, "보존할 첨언", UUID.randomUUID());

		preserve(ARCHIVED_AT, true);

		assertThat(memos.detail(OWNER, memo.memoId())).isEqualTo(memo);
		assertThat(personalMemos(OWNER, ownerCopyId)).isEmpty();
		assertThat(personalComments(OWNER, ownerCopyId)).isEmpty();
		assertThat(personalComments(PARTNER, partnerCopyId)).isEmpty();
		preserve(ARCHIVED_AT, false);
		assertThat(personalMemos(OWNER, ownerCopyId)).containsExactly(onCopy(memo, ownerCopyId));
		assertThat(personalMemos(PARTNER, partnerCopyId)).isEmpty();
		assertThat(personalComments(OWNER, ownerCopyId)).singleElement()
				.satisfies(archive -> assertArchive(archive, comment, ownerCopyId, false));
		assertThat(personalComments(PARTNER, partnerCopyId)).singleElement()
				.satisfies(archive -> assertArchive(archive, comment, partnerCopyId, true));
	}

	private void preserve(Instant archivedAt, boolean rollback) {
		var transaction = new TransactionTemplate(transactionManager);
		transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
		transaction.executeWithoutResult(status -> {
			couples.findMemberCoupleForUpdate(OWNER, coupleId).orElseThrow();
			memoTransfer.copyForUnshare(sharedCourseId, OWNER, ownerCopyId);
			memoTransfer.copyForUnshare(sharedCourseId, PARTNER, partnerCopyId);
			commentArchive.copyForUnshare(sharedCourseId, ownerCopyId, partnerCopyId, archivedAt);
			if (rollback) {
				status.setRollbackOnly();
			}
		});
	}

	private Course saveCourse(String name) {
		Course course = courses.save(Course.create(name));
		courseIds.add(course.getId());
		return course;
	}

	private long savePersonalCourse(long memberId, String name) {
		Course course = saveCourse(name);
		libraries.save(CourseLibrary.create(course, memberId));
		return course.getId();
	}

	private MemoResponse createMemo(long authorId, MemoTargetType targetType, long targetId, String content) {
		return memos.create(authorId, UUID.randomUUID(), new CreateMemoRequest(targetType, targetId, content));
	}

	private List<MemoResponse> personalMemos(long memberId, long courseId) {
		return memos.list(memberId, MemoTargetType.COURSE, courseId, null, 20).items();
	}

	private List<CommentResponse> personalComments(long memberId, long courseId) {
		return comments.list(memberId, CommentTargetType.COURSE, courseId, null, 20).items();
	}

	private static MemoResponse onCopy(MemoResponse source, long courseId) {
		return new MemoResponse(source.memoId(), MemoTargetType.COURSE, courseId, source.content(),
				source.createdAt(), source.updatedAt());
	}

	private static void assertArchive(CommentResponse archive, CommentResponse source, long courseId, boolean mine) {
		assertThat(archive).isEqualTo(new CommentResponse(archive.commentId(), CommentTargetType.COURSE, courseId,
				source.author(), source.content(), mine, true, ARCHIVED_AT, true, false, false,
				source.createdAt(), source.updatedAt()));
	}

	private static void assertCode(String code, Runnable request) {
		assertThatExceptionOfType(BusinessException.class).isThrownBy(request::run)
				.satisfies(exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo(code));
	}
}
