package com.hsu.oneclickmeet.domain.place.controller;

import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.place.entity.PlaceProvider;
import com.hsu.oneclickmeet.domain.place.entity.SavedPlace;
import com.hsu.oneclickmeet.domain.place.entity.SavedPlaceTag;
import com.hsu.oneclickmeet.domain.place.repository.SavedPlaceRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class SharedPlaceIntegrationTests {

	private static final String SHARE = "/api/v1/saved-places/{savedPlaceId}/share";
	private static final String LIST = "/api/v1/couples/me/places";
	private static final Instant NOW = Instant.parse("2026-11-25T06:00:00Z");
	private static final AtomicLong MEMBER_IDS = new AtomicLong(960_000);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private CoupleRepository couples;

	@Autowired
	private SavedPlaceRepository savedPlaces;

	@MockitoBean(name = "coupleClock")
	private Clock clock;

	private long owner;
	private long partner;
	private Couple couple;

	@BeforeEach
	void connectMembers() {
		given(clock.instant()).willReturn(NOW);
		owner = MEMBER_IDS.incrementAndGet();
		partner = MEMBER_IDS.incrementAndGet();
		couple = couples.save(Couple.connect(owner, partner, NOW));
	}

	@Test
	void bothMembersSeeTheSameSnapshotAndItSurvivesPersonalDeletion() throws Exception {
		given(clock.instant()).willReturn(NOW.plusNanos(987_654_321));
		long source = savePlace(owner, "100", "예시 카페");
		var shared = JsonPath.parse(share(owner, source).andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.providerPlaceId").value("100"))
			.andExpect(jsonPath("$.data.name").value("예시 카페"))
			.andExpect(jsonPath("$.data.address").value("서울 성북구 주소"))
			.andExpect(jsonPath("$.data.lat").value(37.5822))
			.andExpect(jsonPath("$.data.lng").value(127.0105))
			.andExpect(jsonPath("$.data.tags[0].label").value("아늑해요"))
			.andExpect(jsonPath("$.data.tags[0].mentionCount").value(12))
			.andReturn().getResponse().getContentAsString());
		long sharedPlaceId = shared.read("$.data.sharedPlaceId", Long.class);
		String sharedAt = shared.read("$.data.sharedAt", String.class);

		for (long memberId : new long[] {owner, partner}) {
			mvc.perform(get(LIST).with(member(memberId))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.coupleId").value(couple.getId()))
				.andExpect(jsonPath("$.data.relationshipStatus").value("CONNECTED"))
				.andExpect(jsonPath("$.data.readOnly").value(false))
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].sharedPlaceId").value(sharedPlaceId))
				.andExpect(jsonPath("$.data.items[0].sharedAt").value(sharedAt));
		}

		mvc.perform(delete("/api/v1/saved-places/{savedPlaceId}", source).with(member(owner)))
			.andExpect(status().isNoContent());
		mvc.perform(get(LIST).with(member(partner))).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].sharedPlaceId").value(sharedPlaceId))
			.andExpect(jsonPath("$.data.items[0].name").value("예시 카페"))
			.andExpect(jsonPath("$.data.items[0].tags[0].label").value("아늑해요"));
	}

	@Test
	void reusesTheFirstShareWhenEitherMemberSharesTheSameProviderPlace() throws Exception {
		long source = savePlace(owner, "100", "처음 공유한 이름");
		long partnerSource = savePlace(partner, "100", "상대방이 저장한 이름");
		long sharedPlaceId = createShare(owner, source);

		share(owner, source).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sharedPlaceId").value(sharedPlaceId));
		share(partner, partnerSource).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sharedPlaceId").value(sharedPlaceId))
			.andExpect(jsonPath("$.data.name").value("처음 공유한 이름"));
		mvc.perform(get(LIST).with(member(owner))).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(1));
	}

	@Test
	void concurrentSharesFromBothMembersReturnTheSameId() throws Exception {
		long source = savePlace(owner, "100", "내가 저장한 장소");
		long partnerSource = savePlace(partner, "100", "상대가 저장한 장소");
		var start = new CyclicBarrier(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> shareTogether(start, owner, source));
			var second = executor.submit(() -> shareTogether(start, partner, partnerSource));
			var firstResponse = first.get(10, TimeUnit.SECONDS);
			var secondResponse = second.get(10, TimeUnit.SECONDS);
			assertThat(List.of(firstResponse.getStatus(), secondResponse.getStatus())).containsExactlyInAnyOrder(201, 200);
			assertThat(JsonPath.parse(firstResponse.getContentAsString()).read("$.data.sharedPlaceId", Long.class))
				.isEqualTo(JsonPath.parse(secondResponse.getContentAsString()).read("$.data.sharedPlaceId", Long.class));
		}
	}

	@Test
	void requiresSignInAndKeepsEachCouplesPlacesPrivate() throws Exception {
		long source = savePlace(owner, "100", "우리 장소");
		createShare(owner, source);
		long outsider = MEMBER_IDS.incrementAndGet();
		long outsiderPartner = MEMBER_IDS.incrementAndGet();

		mvc.perform(post(SHARE, source)).andExpect(status().isUnauthorized());
		mvc.perform(get(LIST)).andExpect(status().isUnauthorized());
		mvc.perform(get(LIST).with(member(outsider))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COUPLE-0409-02"));
		share(outsider, savePlace(outsider, "100", "연결 전 저장한 장소")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COUPLE-0409-02"));
		long outsiderCoupleId = couples.save(Couple.connect(outsider, outsiderPartner, NOW)).getId();
		mvc.perform(get(LIST).with(member(outsider))).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.coupleId").value(outsiderCoupleId))
			.andExpect(jsonPath("$.data.items").isEmpty());
		share(outsider, source).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("PLACE-0404"));
		share(partner, source).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("PLACE-0404"));
	}

	@Test
	void keepsSharedPlacesReadOnlyThroughGraceAndHidesThemAfterExpiry() throws Exception {
		long sharedPlaceId = createShare(owner, savePlace(owner, "100", "공유 장소"));
		long unsharedPlaceId = savePlace(partner, "200", "개인 장소");
		couple.disconnect(NOW);
		couples.save(couple);

		mvc.perform(get(LIST).with(member(partner))).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.relationshipStatus").value("GRACE"))
			.andExpect(jsonPath("$.data.readOnly").value(true))
			.andExpect(jsonPath("$.data.graceEndsAt").value(couple.getGraceEndsAt().toString()))
			.andExpect(jsonPath("$.data.items[0].sharedPlaceId").value(sharedPlaceId));
		share(partner, unsharedPlaceId).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COUPLE-0409-01"));

		given(clock.instant()).willReturn(couple.getGraceEndsAt());
		mvc.perform(get(LIST).with(member(partner))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("COUPLE-0409-02"));
	}

	@Test
	void returnsTheNextPageWithoutRepeatingItems() throws Exception {
		long older = createShare(owner, savePlace(owner, "100", "첫 장소"));
		long newer = createShare(owner, savePlace(owner, "200", "둘째 장소"));

		var first = mvc.perform(get(LIST).with(member(partner)).param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].sharedPlaceId").value(newer))
			.andExpect(jsonPath("$.data.hasNext").value(true))
			.andReturn().getResponse();
		String cursor = JsonPath.parse(first.getContentAsString()).read("$.data.nextCursor", String.class);
		mvc.perform(get(LIST).with(member(partner)).param("size", "1").param("cursor", cursor))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].sharedPlaceId").value(older))
			.andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
			.andExpect(jsonPath("$.data.hasNext").value(false));
	}

	private long savePlace(long memberId, String providerPlaceId, String name) {
		return savedPlaces.save(SavedPlace.create(memberId, PlaceProvider.KAKAO, providerPlaceId, name,
			"서울 성북구 주소", null, "음식점 > 카페", "CE7", 37.5822, 127.0105, null,
			List.of(new SavedPlaceTag("아늑해요", 12)))).getId();
	}

	private long createShare(long memberId, long source) throws Exception {
		var response = share(memberId, source).andExpect(status().isCreated()).andReturn().getResponse();
		return JsonPath.parse(response.getContentAsString()).read("$.data.sharedPlaceId", Long.class);
	}

	private ResultActions share(long memberId, long source) throws Exception {
		return mvc.perform(post(SHARE, source).with(member(memberId)));
	}

	private MockHttpServletResponse shareTogether(CyclicBarrier start, long memberId, long source) throws Exception {
		start.await(5, TimeUnit.SECONDS);
		return share(memberId, source).andReturn().getResponse();
	}

	private static RequestPostProcessor member(long memberId) {
		return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
	}
}
