package com.hsu.oneclickmeet.domain.course.controller;

import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.MenuRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.PlaceRequest;
import com.hsu.oneclickmeet.domain.course.repository.CourseRequestLock;
import com.hsu.oneclickmeet.domain.course.service.CoursePlaceLookup;
import com.hsu.oneclickmeet.domain.course.service.CourseSaveService;
import com.hsu.oneclickmeet.domain.course.service.CourseShareService;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SharedCourseUpdateIntegrationTests {

	private static final String COURSE = "/api/v1/shared-courses/{sharedCourseId}";
	private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
	private static final AtomicLong MEMBER_IDS = new AtomicLong(50_000);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private CourseSaveService courseSaveService;

	@Autowired
	private CourseShareService courseShareService;

	@Autowired
	private CoupleRepository coupleRepository;

	@Autowired
	private CourseRequestLock requestLock;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@MockitoBean(name = "coupleClock")
	private Clock clock;

	@MockitoBean
	private CoursePlaceLookup placeLookup;

	private long owner;
	private long partner;
	private long coupleId;
	private long sharedCourseId;
	private List<Long> coursePlaceIds;
	private List<Long> menuIds;

	@BeforeEach
	void createSharedCourse() throws Exception {
		given(clock.instant()).willReturn(NOW);
		jdbc.execute("create table if not exists members (id bigint primary key, name varchar(100) not null)");
		owner = MEMBER_IDS.incrementAndGet();
		partner = MEMBER_IDS.incrementAndGet();
		jdbc.update("insert into members (id, name) values (?, '공유자'), (?, '상대방')", owner, partner);
		coupleId = coupleRepository.save(Couple.connect(owner, partner, NOW)).getId();

		var request = new CreateCourseRequest("연남 코스", List.of(
			new PlaceRequest(501L, List.of(new MenuRequest(null, "파스타", 25_000, true),
				new MenuRequest(null, "샐러드", 12_000, false))),
			new PlaceRequest(502L, List.of()), new PlaceRequest(503L, List.of())));
		long sourceCourseId = courseSaveService.save(owner, UUID.randomUUID(), request,
			(memberId, placeId) -> Optional.of("장소 " + placeId));
		sharedCourseId = courseShareService.share(owner, sourceCourseId, UUID.randomUUID()).sharedCourseId();

		var response = detail().andExpect(status().isOk()).andReturn().getResponse();
		var body = JsonPath.parse(response.getContentAsString());
		coursePlaceIds = body.<List<Number>>read("$.data.places[*].coursePlaceId").stream()
			.map(Number::longValue).toList();
		menuIds = body.<List<Number>>read("$.data.places[0].menuList[*].menuId").stream()
			.map(Number::longValue).toList();
	}

	@AfterEach
	void removeMemberFixtures() {
		jdbc.update("delete from members where id in (?, ?)", owner, partner);
	}

	@Test
	void preservesConcurrentChangesToDifferentMenusAndReplacesAllValuesOfTheSameMenu() throws Exception {
		long placeId = coursePlaceIds.getFirst();
		var ready = new CountDownLatch(2);
		var start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> saveMenuWhenReady(owner, menuPatch(placeId, menuIds.get(0),
				"크림 파스타", 30_000, true), ready, start));
			var second = executor.submit(() -> saveMenuWhenReady(partner, menuPatch(placeId, menuIds.get(1),
				"치킨 샐러드", 15_000, true), ready, start));
			boolean bothReady = ready.await(5, TimeUnit.SECONDS);
			start.countDown();
			assertThat(bothReady).isTrue();
			first.get(10, TimeUnit.SECONDS);
			second.get(10, TimeUnit.SECONDS);
		}
		detail()
			.andExpect(jsonPath("$.data.places[0].menuList[*].menuName").value(contains("크림 파스타", "치킨 샐러드")))
			.andExpect(jsonPath("$.data.places[0].menuList[0].lastEditedBy.memberId").value(owner))
			.andExpect(jsonPath("$.data.places[0].menuList[1].lastEditedBy.memberId").value(partner))
			.andExpect(jsonPath("$.data.selectedTotalPrice").value(45_000));

		Instant later = NOW.plusSeconds(1);
		given(clock.instant()).willReturn(later);
		update(partner, UUID.randomUUID(), menuPatch(placeId, menuIds.get(0), "토마토 파스타", 32_000, false))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sharedCourseId").value(sharedCourseId))
			.andExpect(jsonPath("$.data.structureVersion").value(1))
			.andExpect(jsonPath("$.data.updatedAt").value(later.toString()));
		detail()
			.andExpect(jsonPath("$.data.lastEditedBy.memberId").value(partner))
			.andExpect(jsonPath("$.data.updatedAt").value(later.toString()))
			.andExpect(jsonPath("$.data.places[0].menuList[*].menuName").value(contains("토마토 파스타", "치킨 샐러드")))
			.andExpect(jsonPath("$.data.places[0].menuList[0].price").value(32_000))
			.andExpect(jsonPath("$.data.places[0].menuList[0].selected").value(false))
			.andExpect(jsonPath("$.data.places[0].menuList[0].lastEditedBy.memberId").value(partner))
			.andExpect(jsonPath("$.data.selectedTotalPrice").value(15_000));
	}

	@Test
	void replacesAPlaceAndResolvesItsNewMenusByClientKey() throws Exception {
		UUID clientKey = UUID.randomUUID();
		given(placeLookup.findPlaceName(partner, 504)).willReturn(Optional.of("새 장소"));

		String replacement = """
			{
			  "baseStructureVersion": 1,
			  "placeAdds": [{"clientPlaceKey": "%s", "placeId": 504}],
			  "placeDeletes": [%d],
			  "placeOrder": [{"coursePlaceId": %d}, {"clientPlaceKey": "%s"}, {"coursePlaceId": %d}],
			  "menuUpserts": [{"clientPlaceKey": "%s", "menuId": null, "menuName": "음료", "price": 12000, "selected": true}]
			}
			""".formatted(clientKey, coursePlaceIds.getFirst(), coursePlaceIds.get(2), clientKey,
			coursePlaceIds.get(1), clientKey);
		update(owner, UUID.randomUUID(), replacement)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("PLACE-0404"));
		update(partner, UUID.randomUUID(), replacement)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.structureVersion").value(2));

		detail()
			.andExpect(jsonPath("$.data.structureVersion").value(2))
			.andExpect(jsonPath("$.data.places[*].placeId").value(contains(503, 504, 502)))
			.andExpect(jsonPath("$.data.places[0].coursePlaceId").value(coursePlaceIds.get(2)))
			.andExpect(jsonPath("$.data.places[1].placeName").value("새 장소"))
			.andExpect(jsonPath("$.data.places[1].menuList[0].menuName").value("음료"))
			.andExpect(jsonPath("$.data.places[2].coursePlaceId").value(coursePlaceIds.get(1)));

		update(owner, UUID.randomUUID(), menuPatch(coursePlaceIds.getFirst(), null, "늦게 추가한 메뉴", 1_000, false))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COURSE-0409-03"));
		update(owner, UUID.randomUUID(), menuPatch(coursePlaceIds.get(1), menuIds.getFirst(), "삭제된 메뉴", 1_000, false))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COURSE-0409-03"));
	}

	@Test
	void rejectsStaleStructureAndDeletedMenusWithoutApplyingOtherChanges() throws Exception {
		String order = """
			[{"coursePlaceId": %d}, {"coursePlaceId": %d}, {"coursePlaceId": %d}]
			""".formatted(coursePlaceIds.get(1), coursePlaceIds.get(0), coursePlaceIds.get(2));
		update(owner, UUID.randomUUID(), """
			{"baseStructureVersion": 1, "placeOrder": %s}
			""".formatted(order)).andExpect(status().isOk());
		update(partner, UUID.randomUUID(), """
			{"courseName": "반영되면 안 되는 이름", "baseStructureVersion": 1, "placeOrder": %s}
			""".formatted(order))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COURSE-0409-03"));

		update(owner, UUID.randomUUID(), """
			{"baseStructureVersion": 1, "menuDeletes": [%d]}
			""".formatted(menuIds.getFirst()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.structureVersion").value(2));
		update(partner, UUID.randomUUID(), """
			{
			  "courseName": "반영되면 안 되는 이름",
			  "menuUpserts": [
			    {"coursePlaceId": %d, "menuId": %d, "menuName": "반영되면 안 되는 메뉴", "price": 100, "selected": true},
			    {"coursePlaceId": %d, "menuId": %d, "menuName": "이미 삭제된 메뉴", "price": 100, "selected": true}
			  ]
			}
			""".formatted(coursePlaceIds.getFirst(), menuIds.get(1), coursePlaceIds.getFirst(), menuIds.get(0)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COURSE-0409-03"));

		detail()
			.andExpect(jsonPath("$.data.courseName").value("연남 코스"))
			.andExpect(jsonPath("$.data.structureVersion").value(2))
			.andExpect(jsonPath("$.data.places[*].placeId").value(contains(502, 501, 503)))
			.andExpect(jsonPath("$.data.places[1].menuList.length()").value(1))
			.andExpect(jsonPath("$.data.places[1].menuList[0].menuName").value("샐러드"));
	}

	@Test
	void replaysTheOriginalResponseFor24HoursButChecksCurrentAccess() throws Exception {
		UUID key = UUID.randomUUID();
		String newMenu = menuPatch(coursePlaceIds.getFirst(), null, "감자튀김", 5_000, false);
		var first = update(owner, key, newMenu).andExpect(status().isOk()).andReturn().getResponse();
		given(clock.instant()).willReturn(NOW.plus(Duration.ofHours(24)).minusSeconds(1));
		update(partner, key, "{\"courseName\": \"변경된 코스\"}").andExpect(status().isOk());
		update(owner, key, newMenu)
			.andExpect(status().isOk())
			.andExpect(content().string(first.getContentAsString()));
		update(owner, key, newMenu.replace("감자튀김", "고구마튀김"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COURSE-0409-01"));
		update(MEMBER_IDS.incrementAndGet(), key, newMenu)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("COURSE-0404"));
		detail()
			.andExpect(jsonPath("$.data.courseName").value("변경된 코스"))
			.andExpect(jsonPath("$.data.places[0].menuList[*].menuName").value(contains("파스타", "샐러드", "감자튀김")));

		jdbc.update("update couples set status = 'GRACE', grace_ends_at = date_add(connected_at, interval 2 day) where id = ?",
			coupleId);
		update(owner, key, newMenu)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COUPLE-0409-01"));
	}

	@Test
	void returnsConflictImmediatelyWhileTheSameKeyIsInProgress() throws Exception {
		UUID key = UUID.randomUUID();
		String nameChange = "{\"courseName\": \"바꿀 이름\"}";
		try (var executor = Executors.newSingleThreadExecutor()) {
			new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
				assertThat(requestLock.tryAcquireSharedUpdate(owner, sharedCourseId, key)).isTrue();
				var retry = executor.submit(() -> update(owner, key, nameChange)
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code").value("COURSE-0409-02"))
					.andReturn().getResponse().getStatus());
				assertThat(retry).succeedsWithin(Duration.ofSeconds(5)).isEqualTo(409);
			});
		}
		update(owner, key, nameChange).andExpect(status().isOk());
	}

	@Test
	void rejectsInvalidHeadersAndBodyFields() throws Exception {
		String nameChange = "{\"courseName\": \"새 이름\"}";
		mvc.perform(patch(COURSE, sharedCourseId).with(member(owner)).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(nameChange))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		mvc.perform(patch(COURSE, sharedCourseId).with(member(owner)).with(csrf())
			.header("Idempotency-Key", "1-1-1-1-1").contentType(MediaType.APPLICATION_JSON).content(nameChange))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));

		for (String body : List.of(
			"{}",
			"{\"courseName\": null}",
			"{\"courseName\": \"　 \"}",
			"{\"courseName\": \"새 이름\", \"memo\": \"별도 API 대상\"}",
			"{\"courseName\": \"새 이름\", \"menuDeletes\": null}",
			"{\"placeOrder\": []}",
			"""
			{"menuUpserts": [{"coursePlaceId": %d, "menuName": "음료", "price": 5000, "selected": false}]}
			""".formatted(coursePlaceIds.getFirst()),
			"""
			{"menuUpserts": [{"coursePlaceId": %d, "menuId": null, "menuName": "음료", "price": "5000", "selected": false}]}
			""".formatted(coursePlaceIds.getFirst()))) {
			update(owner, UUID.randomUUID(), body)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		}
	}

	@Test
	void stripsSurroundingWhitespaceBeforeCheckingAndSavingNames() throws Exception {
		String name = "가".repeat(100);
		update(owner, UUID.randomUUID(), """
			{"courseName": "　%s　", "menuUpserts": [
			  {"coursePlaceId": %d, "menuId": %d, "menuName": "  %s  ", "price": 20000, "selected": true}
			]}
			""".formatted(name, coursePlaceIds.getFirst(), menuIds.getFirst(), name))
			.andExpect(status().isOk());
		detail()
			.andExpect(jsonPath("$.data.courseName").value(name))
			.andExpect(jsonPath("$.data.places[0].menuList[0].menuName").value(name));
	}

	private Void saveMenuWhenReady(long memberId, String body, CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
		update(memberId, UUID.randomUUID(), body).andExpect(status().isOk());
		return null;
	}

	private ResultActions update(long memberId, UUID key, String body) throws Exception {
		return mvc.perform(patch(COURSE, sharedCourseId).with(member(memberId)).with(csrf())
			.header("Idempotency-Key", key.toString()).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions detail() throws Exception {
		return mvc.perform(get(COURSE, sharedCourseId).with(member(owner)));
	}

	private static String menuPatch(long coursePlaceId, Long menuId, String name, int price, boolean selected) {
		return """
			{"menuUpserts": [{"coursePlaceId": %d, "menuId": %s, "menuName": "%s", "price": %d, "selected": %b}]}
			""".formatted(coursePlaceId, menuId, name, price, selected);
	}

	private static RequestPostProcessor member(long memberId) {
		return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
	}
}
