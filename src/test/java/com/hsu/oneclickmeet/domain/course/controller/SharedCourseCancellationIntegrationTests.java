package com.hsu.oneclickmeet.domain.course.controller;

import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.MenuRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.PlaceRequest;
import com.hsu.oneclickmeet.domain.course.service.CourseSaveService;
import com.hsu.oneclickmeet.domain.course.service.CourseShareService;
import com.jayway.jsonpath.DocumentContext;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
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
class SharedCourseCancellationIntegrationTests {

	private static final String COURSES = "/api/v1/courses";
	private static final String PERSONAL_COURSE = COURSES + "/{courseId}";
	private static final String SHARED_COURSE = "/api/v1/shared-courses/{sharedCourseId}";
	private static final String MEMOS = "/api/v1/memos";
	private static final String COMMENTS = "/api/v1/comments";
	private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
	private static final AtomicLong MEMBER_IDS = new AtomicLong(70_000);

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private CourseSaveService courseSave;
	@Autowired
	private CourseShareService courseShare;
	@Autowired
	private CoupleRepository couples;
	@MockitoBean(name = "coupleClock")
	private Clock clock;

	private long owner;
	private long partner;
	private long outsider;
	private long coupleId;
	private long sourceCourseId;
	private long sharedCourseId;

	@BeforeEach
	void createSharedCourse() {
		given(clock.instant()).willReturn(NOW);
		jdbc.execute("create table if not exists members (id bigint primary key, name varchar(100) not null)");
		owner = MEMBER_IDS.incrementAndGet();
		partner = MEMBER_IDS.incrementAndGet();
		outsider = MEMBER_IDS.incrementAndGet();
		jdbc.update("insert into members (id, name) values (?, '공유자'), (?, '상대방'), (?, '관계 밖 회원')",
			owner, partner, outsider);
		coupleId = couples.save(Couple.connect(owner, partner, NOW)).getId();
		var request = new CreateCourseRequest("원본 코스", List.of(
			new PlaceRequest(501L, List.of(new MenuRequest(null, "파스타", 25_000, true))),
			new PlaceRequest(502L, List.of()), new PlaceRequest(503L, List.of())));
		sourceCourseId = courseSave.save(owner, UUID.randomUUID(), request, (memberId, placeId) -> Optional.of("장소 " + placeId));
		sharedCourseId = courseShare.share(owner, sourceCourseId, UUID.randomUUID()).sharedCourseId();
	}

	@AfterEach
	void removeMemberFixtures() {
		jdbc.update("delete from members where id in (?, ?, ?)", owner, partner, outsider);
	}

	@Test
	void preservesTheLatestCourseWithNewIdsForEachMemberAndKeepsTheOriginal() throws Exception {
		var shared = json(mvc.perform(get(SHARED_COURSE, sharedCourseId).with(member(owner))).andExpect(status().isOk()));
		var sharedPlaces = ids(shared, "$.data.places[*].coursePlaceId");
		long sharedMenu = shared.read("$.data.places[0].menuList[0].menuId", Long.class);
		var editKey = UUID.randomUUID();
		String edit = """
			{"courseName": "공동 수정 코스", "baseStructureVersion": 1,
			 "placeOrder": [{"coursePlaceId": %d}, {"coursePlaceId": %d}, {"coursePlaceId": %d}],
			 "menuUpserts": [{"coursePlaceId": %d, "menuId": %d, "menuName": "리조또", "price": 18000, "selected": false}]}
			""".formatted(sharedPlaces.get(1), sharedPlaces.get(0), sharedPlaces.get(2), sharedPlaces.getFirst(), sharedMenu);
		update(owner, editKey, edit).andExpect(status().isOk());

		cancel(partner).andExpect(status().isNoContent()).andExpect(content().string(""));
		long ownerCopy = personalCopyId(owner, 2);
		long partnerCopy = personalCopyId(partner, 1);
		assertThat(List.of(sourceCourseId, ownerCopy, partnerCopy)).doesNotHaveDuplicates();
		var ownerCopyDetail = assertLatestCourse(owner, ownerCopy);
		var partnerCopyDetail = assertLatestCourse(partner, partnerCopy);
		assertThat(ids(ownerCopyDetail, "$.data.places[*].coursePlaceId"))
			.doesNotContainAnyElementsOf(sharedPlaces)
			.doesNotContainAnyElementsOf(ids(partnerCopyDetail, "$.data.places[*].coursePlaceId"));
		assertThat(ids(partnerCopyDetail, "$.data.places[*].coursePlaceId")).doesNotContainAnyElementsOf(sharedPlaces);
		assertThat(List.of(sharedMenu,
			ownerCopyDetail.read("$.data.places[1].menuList[0].menuId", Long.class),
			partnerCopyDetail.read("$.data.places[1].menuList[0].menuId", Long.class))).doesNotHaveDuplicates();
		personalDetail(owner, sourceCourseId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.courseName").value("원본 코스"))
			.andExpect(jsonPath("$.data.places[*].placeId").value(contains(501, 502, 503)))
			.andExpect(jsonPath("$.data.places[0].menuList[0].menuName").value("파스타"));
		personalDetail(partner, ownerCopy).andExpect(status().isNotFound());
		personalDetail(owner, partnerCopy).andExpect(status().isNotFound());
		mvc.perform(get(SHARED_COURSE, sharedCourseId).with(member(owner))).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/couples/me/courses").with(member(partner))).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items").isEmpty());
		update(owner, editKey, edit).andExpect(status().isNotFound());
		assertThat(courseShare.share(owner, sourceCourseId, UUID.randomUUID()).sharedCourseId()).isNotEqualTo(sharedCourseId);
	}

	@Test
	void preservesEachAuthorsMemoAndBothReadOnlyCommentsOnlyOnTheOwnersCopy() throws Exception {
		createNote(MEMOS, owner, "COURSE", sourceCourseId, "원본 메모");
		createNote(MEMOS, owner, "SHARED_COURSE", sharedCourseId, "공유자 메모");
		createNote(MEMOS, partner, "SHARED_COURSE", sharedCourseId, "상대방 메모");
		var first = createNote(COMMENTS, owner, "SHARED_COURSE", sharedCourseId, "공유자 첨언");
		long firstId = first.read("$.data.commentId", Long.class);
		Instant editedAt = NOW.plusSeconds(1);
		given(clock.instant()).willReturn(editedAt);
		editComment(owner, firstId, "수정된 공유자 첨언").andExpect(status().isOk());
		var second = createNote(COMMENTS, partner, "SHARED_COURSE", sharedCourseId, "상대방 첨언");
		long secondId = second.read("$.data.commentId", Long.class);
		Instant archivedAt = NOW.plusSeconds(2);
		given(clock.instant()).willReturn(archivedAt);

		cancel(owner).andExpect(status().isNoContent());
		long ownerCopy = personalCopyId(owner, 2);
		long partnerCopy = personalCopyId(partner, 1);
		assertMemo(owner, ownerCopy, "공유자 메모");
		assertMemo(partner, partnerCopy, "상대방 메모");
		assertMemo(owner, sourceCourseId, "원본 메모");
		notes(MEMOS, owner, "COURSE", partnerCopy).andExpect(status().isNotFound());
		notes(COMMENTS, partner, "COURSE", ownerCopy).andExpect(status().isNotFound());
		notes(MEMOS, owner, "SHARED_COURSE", sharedCourseId).andExpect(status().isNotFound());
		notes(COMMENTS, partner, "SHARED_COURSE", sharedCourseId).andExpect(status().isNotFound());
		editComment(owner, firstId, "공동 첨언 수정 시도").andExpect(status().isNotFound());
		var ownerComments = archivedComments(owner, ownerCopy, editedAt, archivedAt);
		var partnerComments = archivedComments(partner, partnerCopy, editedAt, archivedAt);
		List<Long> ownerIds = ids(ownerComments, "$.data.items[*].commentId");
		List<Long> partnerIds = ids(partnerComments, "$.data.items[*].commentId");
		assertThat(ownerIds).doesNotContain(firstId, secondId).doesNotContainAnyElementsOf(partnerIds);
		assertThat(partnerIds).doesNotContain(firstId, secondId);
		editComment(owner, ownerIds.getFirst(), "보존 첨언 수정 시도").andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COMMENT-0409-03"));
		mvc.perform(delete(COMMENTS + "/{commentId}", partnerIds.getLast()).with(member(partner)).with(csrf()))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("COMMENT-0409-03"));
	}

	@Test
	void returnsNoContentForConcurrentCancelsAndRetriesAfterGraceWithoutMakingMoreCopies() throws Exception {
		assertThat(concurrently(() -> cancel(owner).andReturn().getResponse().getStatus(),
			() -> cancel(partner).andReturn().getResponse().getStatus())).containsExactly(204, 204);
		long ownerCopy = personalCopyId(owner, 2);
		long partnerCopy = personalCopyId(partner, 1);
		disconnect();
		cancel(owner).andExpect(status().isNoContent());
		given(clock.instant()).willReturn(NOW.plus(Duration.ofDays(30)));
		cancel(partner).andExpect(status().isNoContent());
		cancel(outsider).andExpect(status().isNotFound());
		assertThat(personalCopyId(owner, 2)).isEqualTo(ownerCopy);
		assertThat(personalCopyId(partner, 1)).isEqualTo(partnerCopy);
	}

	@Test
	void copiesAConcurrentEditIfItCommitsAndRejectsItIfCancellationWins() throws Exception {
		var outcomes = concurrently(
			() -> update(owner, UUID.randomUUID(), "{\"courseName\": \"동시 편집 코스\"}").andReturn().getResponse().getStatus(),
			() -> cancel(partner).andReturn().getResponse().getStatus());
		assertThat(outcomes.getLast()).isEqualTo(204);
		assertThat(outcomes.getFirst()).isIn(200, 404);
		String expectedName = outcomes.getFirst() == 200 ? "동시 편집 코스" : "원본 코스";
		personalDetail(owner, personalCopyId(owner, 2)).andExpect(jsonPath("$.data.courseName").value(expectedName));
		personalDetail(partner, personalCopyId(partner, 1)).andExpect(jsonPath("$.data.courseName").value(expectedName));
	}

	@Test
	void refusesAnOutsiderAndDoesNotCancelForMembersInGrace() throws Exception {
		cancel(outsider).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("COURSE-0404"));
		disconnect();
		cancel(partner).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("COUPLE-0409-01"));
		mvc.perform(get(SHARED_COURSE, sharedCourseId).with(member(owner))).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.readOnly").value(true));
		mvc.perform(get(COURSES).with(member(owner))).andExpect(jsonPath("$.data.items.length()").value(1));
		mvc.perform(get(COURSES).with(member(partner))).andExpect(jsonPath("$.data.items").isEmpty());
	}

	private ResultActions cancel(long memberId) throws Exception {
		return mvc.perform(delete(SHARED_COURSE, sharedCourseId).with(member(memberId)).with(csrf()));
	}

	private ResultActions update(long memberId, UUID key, String body) throws Exception {
		return mvc.perform(patch(SHARED_COURSE, sharedCourseId).with(member(memberId)).with(csrf())
			.header("Idempotency-Key", key.toString()).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private void disconnect() throws Exception {
		mvc.perform(post("/api/v1/couples/{coupleId}/disconnections", coupleId).with(member(owner)).with(csrf())
			.header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
	}

	private long personalCopyId(long memberId, int expectedCount) throws Exception {
		var result = mvc.perform(get(COURSES).with(member(memberId))).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(expectedCount));
		List<Long> copies = ids(json(result), "$.data.items[?(@.originSharedCourseId == " + sharedCourseId + ")].courseId");
		assertThat(copies).hasSize(1);
		return copies.getFirst();
	}

	private ResultActions personalDetail(long memberId, long courseId) throws Exception {
		return mvc.perform(get(PERSONAL_COURSE, courseId).with(member(memberId)));
	}

	private DocumentContext assertLatestCourse(long memberId, long courseId) throws Exception {
		return json(personalDetail(memberId, courseId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.courseName").value("공동 수정 코스"))
			.andExpect(jsonPath("$.data.places[*].placeId").value(contains(502, 501, 503)))
			.andExpect(jsonPath("$.data.places[1].menuList[0].menuName").value("리조또"))
			.andExpect(jsonPath("$.data.places[1].menuList[0].price").value(18_000))
			.andExpect(jsonPath("$.data.places[1].menuList[0].selected").value(false)));
	}

	private DocumentContext createNote(String path, long memberId, String type, long targetId, String note) throws Exception {
		String body = """
			{"targetType": "%s", "targetId": %d, "content": "%s"}
			""".formatted(type, targetId, note);
		return json(mvc.perform(post(path).with(member(memberId)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.header("Idempotency-Key", UUID.randomUUID().toString()).content(body)).andExpect(status().isCreated()));
	}

	private ResultActions notes(String path, long memberId, String type, long targetId) throws Exception {
		return mvc.perform(get(path).with(member(memberId)).param("targetType", type).param("targetId", Long.toString(targetId)));
	}

	private ResultActions editComment(long memberId, long commentId, String updated) throws Exception {
		return mvc.perform(patch(COMMENTS + "/{commentId}", commentId).with(member(memberId)).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"%s\"}".formatted(updated)));
	}

	private void assertMemo(long memberId, long courseId, String expected) throws Exception {
		notes(MEMOS, memberId, "COURSE", courseId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[*].content").value(contains(expected)));
	}

	private DocumentContext archivedComments(long memberId, long courseId, Instant editedAt, Instant archivedAt) throws Exception {
		return json(notes(COMMENTS, memberId, "COURSE", courseId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[*].content").value(contains("수정된 공유자 첨언", "상대방 첨언")))
			.andExpect(jsonPath("$.data.items[0].author.memberId").value(owner))
			.andExpect(jsonPath("$.data.items[1].author.memberId").value(partner))
			.andExpect(jsonPath("$.data.items[0].createdAt").value(NOW.toString()))
			.andExpect(jsonPath("$.data.items[0].updatedAt").value(editedAt.toString()))
			.andExpect(jsonPath("$.data.items[*].archivedAt").value(everyItem(is(archivedAt.toString()))))
			.andExpect(jsonPath("$.data.items[*].readOnly").value(everyItem(is(true))))
			.andExpect(jsonPath("$.data.items[*].editable").value(everyItem(is(false))))
			.andExpect(jsonPath("$.data.items[*].deletable").value(everyItem(is(false)))));
	}

	private static DocumentContext json(ResultActions result) throws Exception {
		return JsonPath.parse(result.andReturn().getResponse().getContentAsString());
	}

	private static List<Long> ids(DocumentContext result, String path) {
		return result.<List<Number>>read(path).stream().map(Number::longValue).toList();
	}

	private static List<Integer> concurrently(Callable<Integer> first, Callable<Integer> second) throws Exception {
		var ready = new CountDownLatch(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var firstResult = executor.submit(() -> runWhenReady(ready, first));
			var secondResult = executor.submit(() -> runWhenReady(ready, second));
			return List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
		}
	}

	private static int runWhenReady(CountDownLatch ready, Callable<Integer> operation) throws Exception {
		ready.countDown();
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		return operation.call();
	}

	private static RequestPostProcessor member(long memberId) {
		return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
	}
}
