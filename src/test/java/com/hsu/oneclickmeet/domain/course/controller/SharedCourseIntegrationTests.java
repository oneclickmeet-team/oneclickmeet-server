package com.hsu.oneclickmeet.domain.course.controller;

import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.MenuRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.PlaceRequest;
import com.hsu.oneclickmeet.domain.course.repository.CourseRequestLock;
import com.hsu.oneclickmeet.domain.course.service.CourseSaveService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// Other test contexts can recreate the shared schema and reset Hibernate's ID sequences.
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class SharedCourseIntegrationTests {

	private static final String SHARE = "/api/v1/courses/{courseId}/shares";
	private static final String LIST = "/api/v1/couples/me/courses";
	private static final String DETAIL = "/api/v1/shared-courses/{sharedCourseId}";
	private static final String DISCONNECT = "/api/v1/couples/{coupleId}/disconnections";
	private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");
	private static final AtomicLong MEMBER_IDS = new AtomicLong(20_000);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private CourseSaveService courseSaveService;

	@Autowired
	private CoupleRepository coupleRepository;

	@Autowired
	private CourseRequestLock requestLock;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@MockitoBean(name = "coupleClock")
	private Clock clock;

	private final List<Long> testMembers = new ArrayList<>();
	private long owner;
	private long partner;
	private long coupleId;

	@BeforeEach
	void connectMembers() {
		given(clock.instant()).willReturn(NOW);
		// 회원 모듈과 연결하기 전까지 표시명 조회에 필요한 회원 계약만 준비한다.
		jdbc.execute("create table if not exists members (id bigint primary key, name varchar(100) not null)");
		owner = createMember("공유자");
		partner = createMember("상대방");
		coupleId = coupleRepository.save(Couple.connect(owner, partner, NOW)).getId();
	}

	@AfterEach
	void removeMemberFixtures() {
		// members는 테스트 스키마의 Hibernate create-drop 대상에 포함되지 않는다.
		testMembers.forEach(memberId -> jdbc.update("delete from members where id = ?", memberId));
	}

	@Test
	void sharesAnIndependentSnapshotWithNewIdsAndWithoutPrivateData() throws Exception {
		long sourceCourseId = savePersonalCourse(owner, "연남 코스");
		long sourcePlaceId = jdbc.queryForObject("select place_id from course_place where course_id = ? and visit_order = 1",
			Long.class, sourceCourseId);
		long sourceMenuId = jdbc.queryForObject("select menu_id from course_place_item where place_id = ? and menu_order = 1",
			Long.class, sourcePlaceId);
		jdbc.update("update course_place set place_memo = '비공개 메모' where place_id = ?", sourcePlaceId);

		long sharedId = createShare(owner, sourceCourseId, UUID.randomUUID());
		var detail = mvc.perform(get(DETAIL, sharedId).with(member(partner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sharedCourseId").value(sharedId))
			.andExpect(jsonPath("$.data.coupleId").value(coupleId))
			.andExpect(jsonPath("$.data.relationshipStatus").value("CONNECTED"))
			.andExpect(jsonPath("$.data.readOnly").value(false))
			.andExpect(jsonPath("$.data.graceEndsAt").value(nullValue()))
			.andExpect(jsonPath("$.data.structureVersion").value(1))
			.andExpect(jsonPath("$.data.lastEditedBy.memberId").value(owner))
			.andExpect(jsonPath("$.data.lastEditedBy.displayName").value("공유자"))
			.andExpect(jsonPath("$.data.courseName").value("연남 코스"))
			.andExpect(jsonPath("$.data.places[*].placeId").value(contains(503, 501, 502)))
			.andExpect(jsonPath("$.data.places[0].placeName").value("장소 503"))
			.andExpect(jsonPath("$.data.places[0].menuList[*].menuName").value(contains("파스타", "샐러드")))
			.andExpect(jsonPath("$.data.places[0].menuList[*].price").value(contains(25_000, 12_000)))
			.andExpect(jsonPath("$.data.places[0].menuList[*].selected").value(contains(true, false)))
			.andExpect(jsonPath("$.data.places[0].menuList[0].lastEditedBy.memberId").value(owner))
			.andExpect(jsonPath("$.data.places[0].selectedSubtotal").value(25_000))
			.andExpect(jsonPath("$.data.selectedTotalPrice").value(25_000))
			.andExpect(jsonPath("$.data.placesWithoutSelectedMenusCount").value(2))
			.andExpect(jsonPath("$.data.places[0].memo").doesNotHaveJsonPath())
			.andReturn().getResponse();

		var body = JsonPath.parse(detail.getContentAsString());
		long copiedPlaceId = body.read("$.data.places[0].coursePlaceId", Long.class);
		long copiedMenuId = body.read("$.data.places[0].menuList[0].menuId", Long.class);
		assertThat(copiedPlaceId).isNotEqualTo(sourcePlaceId);
		assertThat(copiedMenuId).isNotEqualTo(sourceMenuId);
		assertThat(jdbc.queryForObject("select place_memo from course_place where place_id = ?", String.class, copiedPlaceId))
			.isNull();
		assertThat(jdbc.queryForObject("select count(*) from course_library where member_id = ?", Long.class, owner))
			.isOne();

		jdbc.update("update course set course_name = '변경한 원본' where course_id = ?", sourceCourseId);
		jdbc.update("update course_place_item set menu_selected = false where menu_id = ?", sourceMenuId);
		mvc.perform(get(DETAIL, sharedId).with(member(owner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.courseName").value("연남 코스"))
			.andExpect(jsonPath("$.data.places[0].menuList[0].selected").value(true))
			.andExpect(jsonPath("$.data.selectedTotalPrice").value(25_000));
	}

	@Test
	void replaysTheCreatedResponseBefore24HoursAndScopesTheKeyByCourse() throws Exception {
		long firstCourse = savePersonalCourse(owner, "첫 코스");
		long secondCourse = savePersonalCourse(owner, "둘째 코스");
		UUID key = UUID.randomUUID();
		var first = share(owner, firstCourse, key).andExpect(status().isCreated()).andReturn().getResponse();
		long firstSharedId = JsonPath.parse(first.getContentAsString()).read("$.data.sharedCourseId", Long.class);

		assertThat(createShare(owner, secondCourse, key)).isNotEqualTo(firstSharedId);
		share(owner, firstCourse, UUID.randomUUID()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COURSE-0409-04"));

		given(clock.instant()).willReturn(NOW.plus(Duration.ofHours(24)).minusSeconds(1));
		share(owner, firstCourse, key).andExpect(status().isCreated())
			.andExpect(content().string(first.getContentAsString()));
		given(clock.instant()).willReturn(NOW.plus(Duration.ofHours(24)));
		share(owner, firstCourse, key).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COURSE-0409-04"));
		mvc.perform(get(LIST).with(member(owner))).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(2));
	}

	@Test
	void onlyTheOwnerCanShareAndOnlyTheCurrentCoupleCanRead() throws Exception {
		long sourceCourseId = savePersonalCourse(owner, "공동 코스");
		UUID key = UUID.randomUUID();
		long sharedId = createShare(owner, sourceCourseId, key);
		long outsider = createMember("관계 밖 회원");
		share(partner, sourceCourseId, key).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("COURSE-0404"));
		assertSharedDetailNotFound(outsider, sharedId);
		mvc.perform(get(LIST).with(member(outsider))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COUPLE-0409-02"));

		Instant graceEndsAt = NOW.plus(Duration.ofHours(1));
		jdbc.update("""
			update couples set status = 'GRACE', grace_ends_at = date_add(connected_at, interval 1 hour) where id = ?
			""", coupleId);
		mvc.perform(get(LIST).with(member(partner))).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.relationshipStatus").value("GRACE"))
			.andExpect(jsonPath("$.data.readOnly").value(true))
			.andExpect(jsonPath("$.data.graceEndsAt").value(graceEndsAt.toString()))
			.andExpect(jsonPath("$.data.items[0].sharedCourseId").value(sharedId));
		mvc.perform(get(DETAIL, sharedId).with(member(owner))).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.readOnly").value(true));
		share(owner, sourceCourseId, key).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COUPLE-0409-01"));

		given(clock.instant()).willReturn(graceEndsAt);
		assertSharedDetailNotFound(owner, sharedId);
		mvc.perform(get(LIST).with(member(partner))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COUPLE-0409-02"));
		share(owner, sourceCourseId, key).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COUPLE-0409-02"));

		long currentCoupleId = coupleRepository.save(Couple.connect(owner, outsider, graceEndsAt)).getId();
		assertSharedDetailNotFound(owner, sharedId);
		mvc.perform(get(LIST).with(member(owner))).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.coupleId").value(currentCoupleId))
			.andExpect(jsonPath("$.data.items").isEmpty());
		share(owner, sourceCourseId, key).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("COURSE-0404"));
		assertThat(createShare(owner, sourceCourseId, UUID.randomUUID())).isNotEqualTo(sharedId);
	}

	@Test
	void keepsSharedCoursesReadableButBlocksNewSharesAfterDisconnection() throws Exception {
		long sourceCourseId = savePersonalCourse(owner, "공동 코스");
		long sharedId = createShare(owner, sourceCourseId, UUID.randomUUID());
		long unsharedCourseId = savePersonalCourse(owner, "개인 코스");
		Instant graceEndsAt = NOW.plus(Duration.ofDays(30));

		mvc.perform(post(DISCONNECT, coupleId).with(member(partner)).with(csrf())
				.header("Idempotency-Key", UUID.randomUUID().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.graceEndsAt").value(graceEndsAt.toString()));

		for (long memberId : new long[] {owner, partner}) {
			mvc.perform(get(DETAIL, sharedId).with(member(memberId))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.readOnly").value(true));
		}
		share(owner, unsharedCourseId, UUID.randomUUID()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COUPLE-0409-01"));

		given(clock.instant()).willReturn(graceEndsAt);
		assertSharedDetailNotFound(owner, sharedId);
		assertSharedDetailNotFound(partner, sharedId);
	}

	@Test
	void paginatesByShareTimeAndBreaksTiesBySharedId() throws Exception {
		long lowerTiedId = createShare(owner, savePersonalCourse(owner, "낮은 ID"), UUID.randomUUID());
		long newest = createShare(partner, savePersonalCourse(partner, "최신 코스"), UUID.randomUUID());
		long higherTiedId = createShare(owner, savePersonalCourse(owner, "높은 ID"), UUID.randomUUID());
		jdbc.update("""
			update shared_course set created_at = (select connected_at from couples where id = ?)
			where shared_course_id in (?, ?)
			""", coupleId, lowerTiedId, higherTiedId);
		jdbc.update("""
			update shared_course set created_at = (select date_add(connected_at, interval 1 second) from couples where id = ?)
			where shared_course_id = ?
			""", coupleId, newest);

		var firstPage = mvc.perform(get(LIST).param("size", "2").with(member(partner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(2))
			.andExpect(jsonPath("$.data.items[0].sharedCourseId").value(newest))
			.andExpect(jsonPath("$.data.items[0].courseName").value("최신 코스"))
			.andExpect(jsonPath("$.data.items[0].placeCount").value(3))
			.andExpect(jsonPath("$.data.items[0].sharedAt").value(NOW.plusSeconds(1).toString()))
			.andExpect(jsonPath("$.data.items[1].sharedCourseId").value(higherTiedId))
			.andExpect(jsonPath("$.data.hasNext").value(true))
			.andReturn().getResponse();
		String cursor = JsonPath.read(firstPage.getContentAsString(), "$.data.nextCursor");
		mvc.perform(get(LIST).param("size", "2").param("cursor", cursor).with(member(partner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].sharedCourseId").value(lowerTiedId))
			.andExpect(jsonPath("$.data.hasNext").value(false))
			.andExpect(jsonPath("$.data.nextCursor").value(nullValue()));

		long otherMember = createMember("다른 관계 회원");
		coupleRepository.save(Couple.connect(otherMember, createMember("다른 관계 상대방"), NOW));
		mvc.perform(get(LIST).param("size", "2").param("cursor", cursor).with(member(otherMember)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
	}

	@Test
	void rejectsMissingAndMalformedShareKeysAndInvalidIds() throws Exception {
		long sourceCourseId = savePersonalCourse(owner, "공유할 코스");
		mvc.perform(post(SHARE, sourceCourseId).with(member(owner)).with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		mvc.perform(post(SHARE, sourceCourseId).with(member(owner)).with(csrf()).header("Idempotency-Key", "1-1-1-1-1"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		share(owner, 0, UUID.randomUUID()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		mvc.perform(get(DETAIL, 0).with(member(owner))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		mvc.perform(get(LIST).param("size", "101").with(member(owner))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
	}

	@Test
	void allowsOnlyOneConcurrentShareOfTheSameCourseWithDifferentKeys() throws Exception {
		long sourceCourseId = savePersonalCourse(owner, "동시 공유 코스");
		var ready = new CountDownLatch(2);
		var start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> concurrentShare(sourceCourseId, ready, start));
			var second = executor.submit(() -> concurrentShare(sourceCourseId, ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
				.containsExactlyInAnyOrder(201, 409);
		}
		mvc.perform(get(LIST).with(member(owner))).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(1));
	}

	@Test
	void reportsAnInProgressRetryAndAllowsItAfterTheLockIsReleased() throws Exception {
		long sourceCourseId = savePersonalCourse(owner, "처리 중인 코스");
		UUID key = UUID.randomUUID();
		try (var executor = Executors.newSingleThreadExecutor()) {
			new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
				assertThat(requestLock.tryAcquireShare(owner, sourceCourseId, key)).isTrue();
				var retry = executor.submit(() -> share(owner, sourceCourseId, key)
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code").value("COURSE-0409-02"))
					.andReturn().getResponse().getStatus());
				assertThat(retry).succeedsWithin(Duration.ofSeconds(5)).isEqualTo(409);
			});
		}
		long sharedCourseId = createShare(owner, sourceCourseId, key);
		assertThat(createShare(owner, sourceCourseId, key)).isEqualTo(sharedCourseId);
	}

	private int concurrentShare(long sourceCourseId, CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
		var response = share(owner, sourceCourseId, UUID.randomUUID()).andReturn().getResponse();
		if (response.getStatus() == 409) {
			assertThat(JsonPath.<String>read(response.getContentAsString(), "$.error.code")).isEqualTo("COURSE-0409-04");
		}
		return response.getStatus();
	}

	private long createMember(String name) {
		long memberId = MEMBER_IDS.incrementAndGet();
		jdbc.update("insert into members (id, name) values (?, ?)", memberId, name);
		testMembers.add(memberId);
		return memberId;
	}

	private long savePersonalCourse(long memberId, String name) {
		var request = new CreateCourseRequest(name, List.of(
			new PlaceRequest(503L, List.of(new MenuRequest(null, "파스타", 25_000, true),
				new MenuRequest(null, "샐러드", 12_000, false))),
			new PlaceRequest(501L, List.of()), new PlaceRequest(502L, List.of())));
		return courseSaveService.save(memberId, UUID.randomUUID(), request, (ownerId, placeId) -> Optional.of("장소 " + placeId));
	}

	private long createShare(long memberId, long sourceCourseId, UUID key) throws Exception {
		var response = share(memberId, sourceCourseId, key).andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.sourceCourseId").value(sourceCourseId))
			.andExpect(jsonPath("$.error").value(nullValue()))
			.andReturn().getResponse();
		return JsonPath.parse(response.getContentAsString()).read("$.data.sharedCourseId", Long.class);
	}

	private ResultActions share(long memberId, long sourceCourseId, UUID key) throws Exception {
		return mvc.perform(post(SHARE, sourceCourseId).with(member(memberId)).with(csrf()).header("Idempotency-Key", key.toString()));
	}

	private void assertSharedDetailNotFound(long memberId, long sharedCourseId) throws Exception {
		mvc.perform(get(DETAIL, sharedCourseId).with(member(memberId))).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("COURSE-0404"));
	}

	private static RequestPostProcessor member(long memberId) {
		return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
	}
}
