package com.hsu.oneclickmeet.domain.couple.service;

import com.hsu.oneclickmeet.domain.comment.dto.response.CommentResponse;
import com.hsu.oneclickmeet.domain.comment.service.CommentArchiveService;
import com.hsu.oneclickmeet.domain.comment.service.CommentService;
import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.MenuRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.PlaceRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.UpdateSharedCourseRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.UpdateSharedCourseRequest.MenuUpsert;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseListResponse;
import com.hsu.oneclickmeet.domain.course.dto.response.SharedCourseDetailResponse;
import com.hsu.oneclickmeet.domain.course.service.CourseReadService;
import com.hsu.oneclickmeet.domain.course.service.CourseSaveService;
import com.hsu.oneclickmeet.domain.course.service.CourseShareService;
import com.hsu.oneclickmeet.domain.course.service.SharedCourseCancellationService;
import com.hsu.oneclickmeet.domain.course.service.SharedCourseReadService;
import com.hsu.oneclickmeet.domain.course.service.SharedCourseUpdateService;
import com.hsu.oneclickmeet.domain.memo.dto.request.CreateMemoRequest;
import com.hsu.oneclickmeet.domain.memo.dto.response.MemoResponse;
import com.hsu.oneclickmeet.domain.memo.entity.MemoTargetType;
import com.hsu.oneclickmeet.domain.memo.service.MemoService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static com.hsu.oneclickmeet.domain.comment.entity.CommentTargetType.COURSE;
import static com.hsu.oneclickmeet.domain.comment.entity.CommentTargetType.SHARED_COURSE;
import static com.hsu.oneclickmeet.domain.couple.entity.InvitationPurpose.RECONNECT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
class CoupleGraceExpirationIntegrationTests {

	private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
	private static final AtomicLong MEMBER_IDS = new AtomicLong(410_000);

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private CoupleRepository couples;
	@Autowired
	private CoupleService coupleService;
	@Autowired
	private CoupleDisconnectionService disconnections;
	@Autowired
	private CoupleGraceExpirationJob expirationJob;
	@Autowired
	private CourseSaveService courseSave;
	@Autowired
	private CourseShareService courseShare;
	@Autowired
	private CourseReadService courseRead;
	@Autowired
	private SharedCourseReadService sharedRead;
	@Autowired
	private SharedCourseUpdateService sharedUpdate;
	@Autowired
	private SharedCourseCancellationService cancellations;
	@Autowired
	private MemoService memos;
	@Autowired
	private CommentService comments;
	@MockitoSpyBean
	private CommentArchiveService commentArchive;
	@MockitoBean(name = "coupleClock")
	private Clock clock;

	private long owner;
	private long partner;
	private long coupleId;

	@BeforeEach
	void createCouple() {
		given(clock.instant()).willReturn(NOW);
		jdbc.execute("create table if not exists members (id bigint primary key, name varchar(100) not null)");
		owner = MEMBER_IDS.incrementAndGet();
		partner = MEMBER_IDS.incrementAndGet();
		jdbc.update("insert into members (id, name) values (?, '공유자'), (?, '상대방')", owner, partner);
		coupleId = couples.save(Couple.connect(owner, partner, NOW)).getId();
	}

	@AfterEach
	void removeMembers() {
		jdbc.update("delete from members where id in (?, ?)", owner, partner);
	}

	@Test
	void endsAccessAtTheDeadlineAndKeepsBothPersonalCopiesOnRepeatedRuns() {
		var course = shareCourse("기존 개인 코스");
		var originalMemo = createMemo(owner, MemoTargetType.COURSE, course.sourceId(), "개인 원본 메모");
		var ownerMemo = createMemo(owner, MemoTargetType.SHARED_COURSE, course.sharedId(), "공유자 메모");
		var partnerMemo = createMemo(partner, MemoTargetType.SHARED_COURSE, course.sharedId(), "상대방 메모");
		var firstComment = comments.create(owner, course.sharedId(), "공유자 첨언", UUID.randomUUID());
		given(clock.instant()).willReturn(NOW.plusSeconds(1));
		firstComment = comments.update(owner, firstComment.commentId(), "수정한 공유자 첨언");
		var secondComment = comments.create(partner, course.sharedId(), "상대방 첨언", UUID.randomUUID());
		var firstPlace = sharedRead.detail(owner, course.sharedId()).places().getFirst();
		var editedMenu = new MenuUpsert(firstPlace.coursePlaceId(), null, firstPlace.menuList().getFirst().menuId(),
				"리조또", 18_000, true);
		sharedUpdate.update(partner, course.sharedId(), UUID.randomUUID(),
				new UpdateSharedCourseRequest("공동 코스 최신본", null, null, null, null, List.of(editedMenu), null));
		Instant deadline = disconnect();
		given(clock.instant()).willReturn(deadline.minus(1, ChronoUnit.MICROS));
		String code = coupleService.issueInvitation(owner, RECONNECT).invitationCode();

		expirationJob.expireGraceAndCopyCourses();

		var snapshot = sharedRead.detail(partner, course.sharedId());
		assertThat(snapshot.readOnly()).isTrue();
		assertThat(couples.findById(coupleId).orElseThrow().getStatus()).isEqualTo(CoupleStatus.GRACE);
		assertThat(copies(partner, course.sharedId())).isEmpty();

		given(clock.instant()).willReturn(deadline);
		assertThat(coupleService.getMyCouple(owner).couple()).isNull();
		assertCode("COURSE-0404", () -> sharedRead.detail(partner, course.sharedId()));
		assertCode("COURSE-0404", () -> comments.list(owner, SHARED_COURSE, course.sharedId(), null, 20));
		assertCode("COURSE-0404", () -> memos.list(partner, MemoTargetType.SHARED_COURSE, course.sharedId(), null, 20));
		assertCode("COUPLE-0410", () -> coupleService.reconnect(partner, coupleId, UUID.randomUUID(), code));

		expirationJob.expireGraceAndCopyCourses();

		var ended = couples.findById(coupleId).orElseThrow();
		assertThat(ended.getStatus()).isEqualTo(CoupleStatus.ENDED);
		assertThat(ended.getGraceEndsAt()).isEqualTo(deadline);
		assertCode("COUPLE-0410", () -> coupleService.reconnect(partner, coupleId, UUID.randomUUID(), code));
		assertCode("COURSE-0404", () -> sharedRead.detail(owner, course.sharedId()));
		assertCode("COUPLE-0409-02", () -> cancellations.cancel(partner, course.sharedId()));
		long ownerCopy = onlyCopy(owner, course.sharedId());
		long partnerCopy = onlyCopy(partner, course.sharedId());
		assertThat(List.of(course.sourceId(), ownerCopy, partnerCopy)).doesNotHaveDuplicates();
		assertThat(courseRead.detail(owner, course.sourceId()).courseName()).isEqualTo("기존 개인 코스");
		assertCode("COURSE-0404", () -> courseRead.detail(owner, partnerCopy));
		assertCopiedCourse(snapshot, ownerCopy, partnerCopy);
		assertThat(memos.detail(owner, originalMemo.memoId())).isEqualTo(originalMemo);
		assertMemoCopy(owner, ownerCopy, ownerMemo);
		assertMemoCopy(partner, partnerCopy, partnerMemo);
		var ownerArchives = assertArchives(owner, ownerCopy, firstComment, secondComment, deadline);
		var partnerArchives = assertArchives(partner, partnerCopy, firstComment, secondComment, deadline);

		given(clock.instant()).willReturn(deadline.plusSeconds(60));
		expirationJob.expireGraceAndCopyCourses();

		assertThat(onlyCopy(owner, course.sharedId())).isEqualTo(ownerCopy);
		assertThat(onlyCopy(partner, course.sharedId())).isEqualTo(partnerCopy);
		assertThat(comments.list(owner, COURSE, ownerCopy, null, 20).items()).containsExactlyElementsOf(ownerArchives);
		assertThat(comments.list(partner, COURSE, partnerCopy, null, 20).items()).containsExactlyElementsOf(partnerArchives);
	}

	@Test
	void rollsBackTheFailedCourseAndRetriesItWithoutRepeatingTheCompletedCourse() {
		var failed = shareCourse("이전 실패를 재현할 코스");
		var later = shareCourse("같은 실행에서 이전할 코스");
		var ownerMemo = createMemo(owner, MemoTargetType.SHARED_COURSE, failed.sharedId(), "이전할 메모");
		var comment = comments.create(partner, failed.sharedId(), "이전할 첨언", UUID.randomUUID());
		CommentArchiveService archiveTarget = AopTestUtils.getUltimateTargetObject(commentArchive);
		doAnswer(invocation -> {
			invocation.callRealMethod();
			throw new IllegalStateException("첨언 보존 뒤 이전 실패");
		}).doCallRealMethod().when(archiveTarget).copyForUnshare(eq(failed.sharedId()), anyLong(), anyLong(), any());
		Instant deadline = disconnect();
		given(clock.instant()).willReturn(deadline);

		expirationJob.expireGraceAndCopyCourses();

		assertThat(couples.findById(coupleId).orElseThrow().getStatus()).isEqualTo(CoupleStatus.ENDED);
		assertCode("COURSE-0404", () -> sharedRead.detail(owner, failed.sharedId()));
		assertThat(copies(owner, failed.sharedId())).isEmpty();
		assertThat(copies(partner, failed.sharedId())).isEmpty();
		assertThat(jdbc.queryForObject("select target_id from memos where id = ? and target_type = 'SHARED_COURSE'",
				Long.class, ownerMemo.memoId()))
				.isEqualTo(failed.sharedId());
		long laterOwnerCopy = onlyCopy(owner, later.sharedId());
		long laterPartnerCopy = onlyCopy(partner, later.sharedId());

		expirationJob.expireGraceAndCopyCourses();

		long retriedOwnerCopy = onlyCopy(owner, failed.sharedId());
		long retriedPartnerCopy = onlyCopy(partner, failed.sharedId());
		assertMemoCopy(owner, retriedOwnerCopy, ownerMemo);
		assertThat(memos.list(partner, MemoTargetType.COURSE, retriedPartnerCopy, null, 20).items()).isEmpty();
		assertThat(comments.list(owner, COURSE, retriedOwnerCopy, null, 20).items()).singleElement()
				.satisfies(archive -> assertArchive(archive, comment, retriedOwnerCopy, deadline, false));
		assertThat(comments.list(partner, COURSE, retriedPartnerCopy, null, 20).items()).singleElement()
				.satisfies(archive -> assertArchive(archive, comment, retriedPartnerCopy, deadline, true));
		assertThat(onlyCopy(owner, later.sharedId())).isEqualTo(laterOwnerCopy);
		assertThat(onlyCopy(partner, later.sharedId())).isEqualTo(laterPartnerCopy);
	}

	private SharedFixture shareCourse(String name) {
		var request = new CreateCourseRequest(name, List.of(
				new PlaceRequest(501L, List.of(new MenuRequest(null, "파스타", 25_000, false))),
				new PlaceRequest(502L, List.of()), new PlaceRequest(503L, List.of())));
		long sourceId = courseSave.save(owner, UUID.randomUUID(), request, (memberId, placeId) -> Optional.of("장소 " + placeId));
		long sharedId = courseShare.share(owner, sourceId, UUID.randomUUID()).sharedCourseId();
		return new SharedFixture(sourceId, sharedId);
	}

	private Instant disconnect() {
		return disconnections.disconnect(owner, coupleId, UUID.randomUUID(), false).graceEndsAt();
	}

	private MemoResponse createMemo(long memberId, MemoTargetType targetType, long targetId, String content) {
		return memos.create(memberId, UUID.randomUUID(), new CreateMemoRequest(targetType, targetId, content));
	}

	private long onlyCopy(long memberId, long sharedCourseId) {
		var copies = copies(memberId, sharedCourseId);
		assertThat(copies).hasSize(1);
		return copies.getFirst();
	}

	private List<Long> copies(long memberId, long sharedCourseId) {
		return courseRead.list(memberId, null, 20).items().stream()
				.filter(item -> Objects.equals(item.originSharedCourseId(), sharedCourseId))
				.map(CourseListResponse.Item::courseId)
				.toList();
	}

	private void assertCopiedCourse(SharedCourseDetailResponse snapshot, long ownerCopy, long partnerCopy) {
		var ownerDetail = courseRead.detail(owner, ownerCopy);
		var partnerDetail = courseRead.detail(partner, partnerCopy);
		var sourcePlace = snapshot.places().getFirst();
		var ownerPlace = ownerDetail.places().getFirst();
		var partnerPlace = partnerDetail.places().getFirst();
		assertThat(List.of(sourcePlace.coursePlaceId(), ownerPlace.coursePlaceId(), partnerPlace.coursePlaceId()))
				.doesNotHaveDuplicates();
		assertThat(List.of(sourcePlace.menuList().getFirst().menuId(), ownerPlace.menuList().getFirst().menuId(),
				partnerPlace.menuList().getFirst().menuId())).doesNotHaveDuplicates();
		for (var copy : List.of(ownerDetail, partnerDetail)) {
			assertThat(copy.courseName()).isEqualTo("공동 코스 최신본");
			assertThat(copy.places()).extracting(place -> place.placeId()).containsExactly(501L, 502L, 503L);
			assertThat(copy.places().getFirst().menuList()).singleElement().satisfies(menu -> {
				assertThat(menu.menuName()).isEqualTo("리조또");
				assertThat(menu.price()).isEqualTo(18_000);
				assertThat(menu.selected()).isTrue();
			});
		}
	}

	private void assertMemoCopy(long memberId, long copyId, MemoResponse source) {
		assertThat(memos.list(memberId, MemoTargetType.COURSE, copyId, null, 20).items())
				.containsExactly(new MemoResponse(source.memoId(), MemoTargetType.COURSE, copyId, source.content(),
						source.createdAt(), source.updatedAt()));
	}

	private List<CommentResponse> assertArchives(long memberId, long copyId, CommentResponse first, CommentResponse second,
			Instant archivedAt) {
		var archives = comments.list(memberId, COURSE, copyId, null, 20).items();
		assertThat(archives).hasSize(2);
		assertArchive(archives.get(0), first, copyId, archivedAt, memberId == owner);
		assertArchive(archives.get(1), second, copyId, archivedAt, memberId == partner);
		return archives;
	}

	private static void assertArchive(CommentResponse archive, CommentResponse source, long copyId, Instant archivedAt,
			boolean mine) {
		assertThat(archive.commentId()).isNotEqualTo(source.commentId());
		assertThat(archive).isEqualTo(new CommentResponse(archive.commentId(), COURSE, copyId, source.author(),
				source.content(), mine, true, archivedAt, true, false, false, source.createdAt(), source.updatedAt()));
	}

	private static void assertCode(String code, Runnable operation) {
		assertThatExceptionOfType(BusinessException.class).isThrownBy(operation::run)
				.satisfies(exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo(code));
	}

	private record SharedFixture(long sourceId, long sharedId) {
	}
}
