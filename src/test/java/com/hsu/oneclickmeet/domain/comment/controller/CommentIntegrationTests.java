package com.hsu.oneclickmeet.domain.comment.controller;

import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.PlaceRequest;
import com.hsu.oneclickmeet.domain.course.service.CourseSaveService;
import com.hsu.oneclickmeet.domain.course.service.CourseShareService;
import com.hsu.oneclickmeet.global.persistence.MySqlTransactionLock;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommentIntegrationTests {

	private static final String COMMENTS = "/api/v1/comments";
	private static final String COMMENT = COMMENTS + "/{commentId}";
	private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");
	private static final AtomicLong MEMBER_IDS = new AtomicLong(30_000);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private CoupleRepository couples;

	@Autowired
	private CourseSaveService courseSave;

	@Autowired
	private CourseShareService courseShare;

	@Autowired
	private MySqlTransactionLock requestLock;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@MockitoBean(name = "coupleClock")
	private Clock clock;

	private long owner;
	private long partner;
	private long outsider;
	private long coupleId;
	private long courseId;
	private long sharedCourseId;

	@BeforeEach
	void prepareSharedCourse() {
		given(clock.instant()).willReturn(NOW);
		jdbc.execute("create table if not exists members (id bigint primary key, name varchar(100) not null)");
		owner = MEMBER_IDS.incrementAndGet();
		partner = MEMBER_IDS.incrementAndGet();
		outsider = MEMBER_IDS.incrementAndGet();
		jdbc.update("insert into members (id, name) values (?, '작성자'), (?, '상대방'), (?, '관계 밖 회원')",
			owner, partner, outsider);
		coupleId = couples.save(Couple.connect(owner, partner, NOW)).getId();
		courseId = savePersonalCourse(owner);
		sharedCourseId = courseShare.share(owner, courseId, UUID.randomUUID()).sharedCourseId();
	}

	@AfterEach
	void removeMemberFixtures() {
		jdbc.update("delete from members where id in (?, ?, ?)", owner, partner, outsider);
	}

	@Test
	void showsSharedCommentsToTheCoupleAndOnlyLetsTheAuthorEditOrDelete() throws Exception {
		var created = create(owner, UUID.randomUUID(), "평일 저녁에 가요").andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.targetType").value("SHARED_COURSE"))
			.andExpect(jsonPath("$.data.targetId").value(sharedCourseId))
			.andExpect(jsonPath("$.data.author.memberId").value(owner))
			.andExpect(jsonPath("$.data.author.displayName").value("작성자"))
			.andExpect(jsonPath("$.data.isMine").value(true))
			.andExpect(jsonPath("$.data.editable").value(true))
			.andExpect(jsonPath("$.data.deletable").value(true))
			.andExpect(jsonPath("$.data.archived").value(false))
			.andExpect(jsonPath("$.data.archivedAt").value(nullValue()))
			.andExpect(jsonPath("$.data.readOnly").value(false));
		String createdBody = responseBody(created);
		long commentId = commentId(createdBody);
		String createdAt = JsonPath.read(createdBody, "$.data.createdAt");

		list(partner, "SHARED_COURSE", sharedCourseId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].commentId").value(commentId))
			.andExpect(jsonPath("$.data.items[0].isMine").value(false))
			.andExpect(jsonPath("$.data.items[0].readOnly").value(false))
			.andExpect(jsonPath("$.data.items[0].editable").value(false))
			.andExpect(jsonPath("$.data.items[0].deletable").value(false));
		list(outsider, "SHARED_COURSE", sharedCourseId).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("COURSE-0404"));
		edit(outsider, commentId, "변경").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("COMMENT-0404"));
		edit(partner, commentId, "변경").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("COMMENT-0403"));
		remove(partner, commentId).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("COMMENT-0403"));

		Instant updateTime = NOW.plusSeconds(1);
		given(clock.instant()).willReturn(updateTime);
		edit(owner, commentId, "주말 점심에 가요").andExpect(status().isOk())
			.andExpect(jsonPath("$.data.commentId").value(commentId))
			.andExpect(jsonPath("$.data.createdAt").value(createdAt))
			.andExpect(jsonPath("$.data.updatedAt").value(updateTime.toString()))
			.andExpect(jsonPath("$.data.content").value("주말 점심에 가요"));
		remove(owner, commentId).andExpect(status().isNoContent()).andExpect(content().string(""));
		remove(owner, commentId).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("COMMENT-0404"));
		list(partner, "SHARED_COURSE", sharedCourseId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items").isEmpty());
	}

	@Test
	void replaysTheOriginalCreationAfterEditAndDeletionAndRejectsAChangedRequest() throws Exception {
		UUID key = UUID.randomUUID();
		String created = responseBody(create(owner, key, "첫 본문").andExpect(status().isCreated()));
		long commentId = commentId(created);

		create(owner, key, "다른 본문").andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COMMENT-0409-01"));
		long otherSharedCourse = courseShare.share(owner, savePersonalCourse(owner), UUID.randomUUID()).sharedCourseId();
		create(owner, otherSharedCourse, key, "첫 본문").andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COMMENT-0409-01"));
		edit(owner, commentId, "수정한 본문").andExpect(status().isOk());
		create(owner, key, "첫 본문").andExpect(status().isCreated()).andExpect(content().json(created));
		list(owner, "SHARED_COURSE", sharedCourseId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].content").value("수정한 본문"));

		remove(owner, commentId).andExpect(status().isNoContent());
		given(clock.instant()).willReturn(NOW.plus(Duration.ofHours(24)).minusSeconds(1));
		create(owner, key, "첫 본문").andExpect(status().isCreated()).andExpect(content().json(created));
		list(owner, "SHARED_COURSE", sharedCourseId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items").isEmpty());
		create(partner, key, "상대방 본문").andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.author.memberId").value(partner));
	}

	@Test
	void reportsAnInProgressCreationAndSucceedsAfterTheLockIsReleased() throws Exception {
		UUID key = UUID.randomUUID();
		var locked = new CountDownLatch(1);
		var release = new CompletableFuture<Void>();
		try (var executor = Executors.newFixedThreadPool(2)) {
			var original = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
				assertThat(requestLock.tryAcquire(":comment-create:" + owner + ":" + key)).isTrue();
				locked.countDown();
				release.join();
			}));
			try {
				assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
				var retry = executor.submit(() -> create(owner, key, "재시도할 첨언")
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code").value("COMMENT-0409-02"))
					.andReturn().getResponse().getStatus());
				assertThat(retry).succeedsWithin(Duration.ofSeconds(5)).isEqualTo(409);
			} finally {
				release.complete(null);
			}
			assertThat(original).succeedsWithin(Duration.ofSeconds(5));
		}
		String created = responseBody(create(owner, key, "재시도할 첨언").andExpect(status().isCreated()));
		create(owner, key, "재시도할 첨언").andExpect(status().isCreated()).andExpect(content().json(created));
	}

	@Test
	void blocksWritesAndReplayDuringGraceAndHidesSharedCommentsAtExpiry() throws Exception {
		UUID key = UUID.randomUUID();
		long commentId = commentId(responseBody(create(owner, key, "유예 전 첨언").andExpect(status().isCreated())));
		Instant graceEndsAt = NOW.plus(Duration.ofDays(30));
		mvc.perform(post("/api/v1/couples/{coupleId}/disconnections", coupleId).with(member(owner)).with(csrf())
			.header("Idempotency-Key", UUID.randomUUID().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.graceEndsAt").value(graceEndsAt.toString()));

		list(owner, "SHARED_COURSE", sharedCourseId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].isMine").value(true))
			.andExpect(jsonPath("$.data.items[0].readOnly").value(true))
			.andExpect(jsonPath("$.data.items[0].editable").value(false))
			.andExpect(jsonPath("$.data.items[0].deletable").value(false));
		create(partner, UUID.randomUUID(), "새 첨언").andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COUPLE-0409-01"));
		create(owner, key, "유예 전 첨언").andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COUPLE-0409-01"));
		edit(owner, commentId, "변경").andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COUPLE-0409-01"));
		remove(owner, commentId).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COUPLE-0409-01"));

		given(clock.instant()).willReturn(graceEndsAt.minusNanos(1_000));
		list(partner, "SHARED_COURSE", sharedCourseId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].readOnly").value(true));
		given(clock.instant()).willReturn(graceEndsAt);
		list(owner, "SHARED_COURSE", sharedCourseId).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("COURSE-0404"));
		create(owner, key, "유예 전 첨언").andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COUPLE-0409-02"));
	}

	@Test
	void exposesPreservedRowsOnlyToTheCopyOwnerAndPaginatesOldestFirst() throws Exception {
		long firstId = 100_000 + owner;
		long secondId = 100_000 + partner;
		jdbc.update("""
			insert into comments (id, target_type, target_id, author_member_id, content, created_at, updated_at, archived_at)
			select ?, 'COURSE', ?, ?, ?, connected_at, date_add(connected_at, interval 1 minute),
			       date_add(connected_at, interval 1 hour) from couples where id = ?
			""", secondId, courseId, owner, "내 첨언", coupleId);
		jdbc.update("""
			insert into comments (id, target_type, target_id, author_member_id, content, created_at, updated_at, archived_at)
			select ?, 'COURSE', ?, ?, ?, connected_at, connected_at,
			       date_add(connected_at, interval 1 hour) from couples where id = ?
			""", firstId, courseId, partner, "상대 첨언", coupleId);

		String firstPage = responseBody(mvc.perform(get(COMMENTS).with(member(owner))
			.param("targetType", "COURSE").param("targetId", Long.toString(courseId)).param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].commentId").value(firstId))
			.andExpect(jsonPath("$.data.items[0].targetType").value("COURSE"))
			.andExpect(jsonPath("$.data.items[0].targetId").value(courseId))
			.andExpect(jsonPath("$.data.items[0].author.memberId").value(partner))
			.andExpect(jsonPath("$.data.items[0].createdAt").value(NOW.toString()))
			.andExpect(jsonPath("$.data.items[0].archived").value(true))
			.andExpect(jsonPath("$.data.items[0].archivedAt").value(NOW.plus(Duration.ofHours(1)).toString()))
			.andExpect(jsonPath("$.data.items[0].readOnly").value(true))
			.andExpect(jsonPath("$.data.hasNext").value(true)));
		String cursor = JsonPath.read(firstPage, "$.data.nextCursor");
		mvc.perform(get(COMMENTS).with(member(owner)).param("targetType", "COURSE")
			.param("targetId", Long.toString(courseId)).param("size", "1").param("cursor", cursor))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].commentId").value(secondId))
			.andExpect(jsonPath("$.data.items[0].updatedAt").value(NOW.plusSeconds(60).toString()))
			.andExpect(jsonPath("$.data.items[0].isMine").value(true))
			.andExpect(jsonPath("$.data.items[0].editable").value(false))
			.andExpect(jsonPath("$.data.items[0].deletable").value(false))
			.andExpect(jsonPath("$.data.hasNext").value(false))
			.andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
		mvc.perform(get(COMMENTS).with(member(owner)).param("targetType", "COURSE")
			.param("targetId", Long.toString(savePersonalCourse(owner))).param("size", "1").param("cursor", cursor))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		list(partner, "COURSE", courseId).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("COURSE-0404"));
		edit(owner, secondId, "변경").andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COMMENT-0409-03"));
		remove(owner, secondId).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COMMENT-0409-03"));
		remove(partner, firstId).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("COMMENT-0404"));
	}

	@Test
	void requiresAValidKeyAndEnforcesTheContentLimitOnCreateAndEdit() throws Exception {
		String validBody = createBody("SHARED_COURSE", sharedCourseId, "첨언");
		mvc.perform(post(COMMENTS).with(member(owner)).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(validBody))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		mvc.perform(post(COMMENTS).with(member(owner)).with(csrf()).header("Idempotency-Key", "1-1-1-1-1")
			.contentType(MediaType.APPLICATION_JSON).content(validBody))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		create(owner, UUID.randomUUID(), " ").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		create(owner, UUID.randomUUID(), "\\ud800").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		String limit = "🙂".repeat(1_000);
		create(owner, UUID.randomUUID(), limit + "나").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		var created = create(owner, UUID.randomUUID(), limit).andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.content").value(limit));
		long commentId = commentId(responseBody(created));
		list(owner, "SHARED_COURSE", sharedCourseId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].content").value(limit));
		edit(owner, commentId, limit + "나").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
	}

	private long savePersonalCourse(long memberId) {
		var request = new CreateCourseRequest("연남 코스", List.of(
			new PlaceRequest(501L, List.of()), new PlaceRequest(502L, List.of()), new PlaceRequest(503L, List.of())));
		return courseSave.save(memberId, UUID.randomUUID(), request, (id, placeId) -> Optional.of("장소 " + placeId));
	}

	private ResultActions create(long memberId, UUID key, String body) throws Exception {
		return create(memberId, sharedCourseId, key, body);
	}

	private ResultActions create(long memberId, long targetId, UUID key, String body) throws Exception {
		return mvc.perform(post(COMMENTS).with(member(memberId)).with(csrf()).header("Idempotency-Key", key.toString())
			.contentType(MediaType.APPLICATION_JSON).content(createBody("SHARED_COURSE", targetId, body)));
	}

	private static String createBody(String type, long targetId, String body) {
		return """
			{"targetType": "%s", "targetId": %d, "content": "%s"}
			""".formatted(type, targetId, body);
	}

	private ResultActions list(long memberId, String targetType, long targetId) throws Exception {
		return mvc.perform(get(COMMENTS).with(member(memberId)).param("targetType", targetType)
			.param("targetId", Long.toString(targetId)));
	}

	private ResultActions edit(long memberId, long commentId, String body) throws Exception {
		return mvc.perform(patch(COMMENT, commentId).with(member(memberId)).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"%s\"}".formatted(body)));
	}

	private ResultActions remove(long memberId, long commentId) throws Exception {
		return mvc.perform(delete(COMMENT, commentId).with(member(memberId)).with(csrf()));
	}

	private static String responseBody(ResultActions result) throws Exception {
		return result.andReturn().getResponse().getContentAsString();
	}

	private static long commentId(String body) {
		return JsonPath.parse(body).read("$.data.commentId", Long.class);
	}

	private static RequestPostProcessor member(long memberId) {
		return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
	}
}
