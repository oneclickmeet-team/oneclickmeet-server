package com.hsu.oneclickmeet.domain.couple.service;

import com.hsu.oneclickmeet.domain.couple.dto.response.ConnectionResponse;
import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRequestLock;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.hsu.oneclickmeet.domain.couple.entity.InvitationPurpose.CONNECT;
import static com.hsu.oneclickmeet.domain.couple.entity.InvitationPurpose.RECONNECT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;

@ActiveProfiles("test")
@SpringBootTest
class CoupleServiceIntegrationTests {

	private static final long ISSUER = 10001L;
	private static final long ACCEPTOR = 10002L;
	private static final long THIRD_MEMBER = 10003L;
	private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");

	@Autowired
	private CoupleService coupleService;

	@Autowired
	private CoupleDisconnectionService disconnectionService;

	@Autowired
	private CoupleGraceExpirationService graceExpiration;

	@MockitoSpyBean
	private CoupleAccessService access;

	@Autowired
	private CoupleRepository couples;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@MockitoBean(name = "coupleClock")
	private Clock clock;

	@MockitoSpyBean
	private CoupleRequestLock requestLock;

	@BeforeEach
	void prepareMembers() {
		given(clock.instant()).willReturn(NOW);
		// 회원 모듈이 합류하기 전까지 이름 조회와 회원 행 잠금에 필요한 테스트 데이터만 준비한다.
		jdbc.execute("create table if not exists members (id bigint primary key, name varchar(100) not null)");
		removeFixtures();
		jdbc.update("insert into members (id, name) values (?, '발급자'), (?, '입력자'), (?, '다른 회원')",
			ISSUER, ACCEPTOR, THIRD_MEMBER);
	}

	@AfterEach
	void removeFixtures() {
		jdbc.update("delete from couple_reconnection_replays where member_id in (?, ?, ?)",
			ISSUER, ACCEPTOR, THIRD_MEMBER);
		jdbc.update("delete from couple_disconnection_replays where member_id in (?, ?, ?)",
			ISSUER, ACCEPTOR, THIRD_MEMBER);
		jdbc.update("delete from couple_connection_replays where member_id in (?, ?, ?)", ISSUER, ACCEPTOR, THIRD_MEMBER);
		jdbc.update("delete from couple_invitations where issuer_id in (?, ?, ?)", ISSUER, ACCEPTOR, THIRD_MEMBER);
		jdbc.update("delete from couples where first_member_id in (?, ?, ?) or second_member_id in (?, ?, ?)",
			ISSUER, ACCEPTOR, THIRD_MEMBER, ISSUER, ACCEPTOR, THIRD_MEMBER);
		jdbc.update("delete from members where id in (?, ?, ?)", ISSUER, ACCEPTOR, THIRD_MEMBER);
	}

	@Test
	void replacesAnInvitationAndReturnsOnlyTheIssuersActiveCode() {
		var first = coupleService.issueInvitation(ISSUER, CONNECT);
		assertThat(first.invitationCode()).matches("[A-Z0-9]{3}-[A-Z0-9]{4}");
		assertThat(first.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
		assertThat(coupleService.getMyInvitation(ISSUER, CONNECT).invitation()).isEqualTo(first);
		assertThat(coupleService.getMyInvitation(ACCEPTOR, CONNECT).invitation()).isNull();

		var replacement = coupleService.issueInvitation(ISSUER, CONNECT);

		assertThat(replacement.invitationCode()).isNotEqualTo(first.invitationCode());
		assertThat(coupleService.getMyInvitation(ISSUER, CONNECT).invitation()).isEqualTo(replacement);
		assertBusinessError("INVITATION-0409",
			() -> coupleService.connect(ACCEPTOR, UUID.randomUUID(), first.invitationCode()));
		assertThat(coupleService.getMyCouple(ACCEPTOR).couple()).isNull();
	}

	@Test
	void rejectsSelfAcceptanceAndExpiresAnInvitationAtItsExactDeadline() {
		var invitation = coupleService.issueInvitation(ISSUER, CONNECT);

		assertBusinessError("COUPLE-0409-02",
			() -> coupleService.connect(ISSUER, UUID.randomUUID(), invitation.invitationCode()));
		assertThat(coupleService.getMyInvitation(ISSUER, CONNECT).invitation()).isEqualTo(invitation);
		given(clock.instant()).willReturn(invitation.expiresAt());

		assertThat(coupleService.getMyInvitation(ISSUER, CONNECT).invitation()).isNull();
		assertBusinessError("INVITATION-0410",
			() -> coupleService.connect(ACCEPTOR, UUID.randomUUID(), invitation.invitationCode()));
		assertThat(coupleService.getMyCouple(ACCEPTOR).couple()).isNull();
	}

	@Test
	void consumesAnInvitationAndReplaysTheOriginalConnectionAfterTheInvitationExpires() {
		var invitation = coupleService.issueInvitation(ISSUER, CONNECT);
		coupleService.issueInvitation(ACCEPTOR, CONNECT);
		var otherInvitation = coupleService.issueInvitation(THIRD_MEMBER, CONNECT);
		UUID key = UUID.randomUUID();
		String code = invitation.invitationCode();
		Instant acceptedAt = NOW.plus(Duration.ofHours(2));
		given(clock.instant()).willReturn(acceptedAt);

		var connection = coupleService.connect(ACCEPTOR, key, code);

		assertThat(connection.status()).isEqualTo(CoupleStatus.CONNECTED);
		var issuerCouple = coupleService.getMyCouple(ISSUER).couple();
		assertThat(issuerCouple.coupleId()).isEqualTo(connection.coupleId());
		assertThat(issuerCouple.partner().memberId()).isEqualTo(ACCEPTOR);
		assertThat(issuerCouple.partner().displayName()).isEqualTo("입력자");
		assertThat(issuerCouple.connectedAt()).isEqualTo(acceptedAt);
		assertThat(issuerCouple.disconnectedAt()).isNull();
		assertThat(issuerCouple.graceEndsAt()).isNull();
		assertThat(issuerCouple.remainingGraceDays()).isNull();
		assertThat(issuerCouple.sharedReadOnly()).isFalse();
		assertThat(issuerCouple.canReconnect()).isFalse();
		assertThat(coupleService.getMyCouple(ACCEPTOR).couple().partner().memberId()).isEqualTo(ISSUER);
		assertThat(coupleService.getMyInvitation(ISSUER, CONNECT).invitation()).isNull();
		assertThat(coupleService.getMyInvitation(ACCEPTOR, CONNECT).invitation()).isNull();
		assertBusinessError("INVITATION-0409", () -> coupleService.connect(ACCEPTOR, UUID.randomUUID(), code));
		assertBusinessError("COUPLE-0409-03",
			() -> coupleService.connect(ACCEPTOR, key, otherInvitation.invitationCode()));
		assertBusinessError("COUPLE-0409-02", () -> coupleService.issueInvitation(ACCEPTOR, CONNECT));

		given(clock.instant()).willReturn(acceptedAt.plus(Duration.ofHours(24)).minusSeconds(1));
		String equivalentCode = " " + code.toLowerCase(Locale.ROOT).replace("-", "") + " ";
		assertThat(coupleService.connect(ACCEPTOR, key, equivalentCode)).isEqualTo(connection);
	}

	@Test
	void returnsGraceStatusAndCapsTheReconnectInvitationAtGraceEnd() {
		var invitation = coupleService.issueInvitation(ISSUER, CONNECT);
		var connection = coupleService.connect(ACCEPTOR, UUID.randomUUID(), invitation.invitationCode());
		jdbc.update("""
			update couples
			set status = 'GRACE',
				disconnected_at = date_sub(connected_at, interval 719 hour),
				grace_ends_at = date_add(connected_at, interval 1 hour)
			where id = ?
			""", connection.coupleId());
		Instant graceEndsAt = NOW.plus(Duration.ofHours(1));

		var couple = coupleService.getMyCouple(ISSUER).couple();
		assertThat(couple.status()).isEqualTo(CoupleStatus.GRACE);
		assertThat(couple.graceEndsAt()).isEqualTo(graceEndsAt);
		assertThat(couple.remainingGraceDays()).isEqualTo(1);
		assertThat(couple.sharedReadOnly()).isTrue();
		assertThat(couple.canReconnect()).isTrue();
		assertBusinessError("COUPLE-0409-02", () -> coupleService.issueInvitation(ISSUER, CONNECT));

		var reconnect = coupleService.issueInvitation(ISSUER, RECONNECT);
		assertThat(reconnect.coupleId()).isEqualTo(connection.coupleId());
		assertThat(reconnect.expiresAt()).isEqualTo(graceEndsAt);
		assertThat(coupleService.getMyInvitation(ISSUER, RECONNECT).invitation()).isEqualTo(reconnect);
		assertBusinessError("COUPLE-0409-02",
			() -> coupleService.connect(THIRD_MEMBER, UUID.randomUUID(), reconnect.invitationCode()));
		var newInvitation = coupleService.issueInvitation(THIRD_MEMBER, CONNECT);
		assertBusinessError("COUPLE-0409-02",
			() -> coupleService.connect(ACCEPTOR, UUID.randomUUID(), newInvitation.invitationCode()));

		given(clock.instant()).willReturn(graceEndsAt);
		assertThat(coupleService.getMyCouple(ISSUER).couple()).isNull();
		assertThat(coupleService.getMyInvitation(ISSUER, RECONNECT).invitation()).isNull();
		assertBusinessError("COUPLE-0409-02", () -> coupleService.issueInvitation(ISSUER, RECONNECT));
	}

	@Test
	void allowsOnlyOneOfTwoMembersToAcceptTheSameInvitation() throws Exception {
		String code = coupleService.issueInvitation(ISSUER, CONNECT).invitationCode();

		var attempts = runTogether(
			() -> coupleService.connect(ACCEPTOR, UUID.randomUUID(), code),
			() -> coupleService.connect(THIRD_MEMBER, UUID.randomUUID(), code));

		assertThat(attempts).extracting(ConnectionAttempt::errorCode)
			.containsExactlyInAnyOrder(null, "INVITATION-0409");
		assertThat(countRelationshipsFor(ISSUER)).isOne();
		assertThat(coupleService.getMyInvitation(ISSUER, CONNECT).invitation()).isNull();
	}

	@Test
	void preventsTheSameMemberFromAcceptingTwoDifferentInvitations() throws Exception {
		String firstCode = coupleService.issueInvitation(ISSUER, CONNECT).invitationCode();
		String secondCode = coupleService.issueInvitation(THIRD_MEMBER, CONNECT).invitationCode();

		var attempts = runTogether(
			() -> coupleService.connect(ACCEPTOR, UUID.randomUUID(), firstCode),
			() -> coupleService.connect(ACCEPTOR, UUID.randomUUID(), secondCode));

		assertThat(attempts).extracting(ConnectionAttempt::errorCode)
			.containsExactlyInAnyOrder(null, "COUPLE-0409-02");
		assertThat(countRelationshipsFor(ACCEPTOR)).isOne();
	}

	@Test
	void reusesTheSameResultWhenOneMemberRetriesConcurrentlyWithTheSameKey() throws Exception {
		String code = coupleService.issueInvitation(ISSUER, CONNECT).invitationCode();
		UUID key = UUID.randomUUID();

		Supplier<ConnectionResponse> connect = () -> coupleService.connect(ACCEPTOR, key, code);
		var attempts = runTogether(connect, connect);

		ConnectionResponse replay = connect.get();
		assertThat(attempts).anySatisfy(attempt -> assertThat(attempt.response()).isEqualTo(replay));
		assertThat(attempts).allSatisfy(attempt -> {
			if (attempt.errorCode() == null) {
				assertThat(attempt.response()).isEqualTo(replay);
			} else {
				assertThat(attempt.errorCode()).isEqualTo("COUPLE-0409-04");
			}
		});
		assertThat(countRelationshipsFor(ACCEPTOR)).isOne();
	}

	@Test
	void reportsAnInProgressRetryWhileItsReplayRowIsLocked() throws Exception {
		String code = coupleService.issueInvitation(ISSUER, CONNECT).invitationCode();
		UUID key = UUID.randomUUID();
		jdbc.update("insert into couple_connection_replays (member_id, request_key) values (?, ?)",
			ACCEPTOR, key.toString());
		var replayLocked = new CountDownLatch(1);
		var releaseReplay = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var lock = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
				jdbc.queryForObject("""
					select id from couple_connection_replays
					where member_id = ? and request_key = ? for update
					""", Long.class, ACCEPTOR, key.toString());
				replayLocked.countDown();
				awaitRelease(releaseReplay);
			}));
			assertThat(replayLocked.await(5, TimeUnit.SECONDS)).isTrue();
			var retry = executor.submit(() -> assertBusinessError("COUPLE-0409-04",
				() -> coupleService.connect(ACCEPTOR, key, code)));
			retry.get(5, TimeUnit.SECONDS);
			releaseReplay.countDown();
			lock.get(5, TimeUnit.SECONDS);
		} finally {
			releaseReplay.countDown();
			executor.shutdownNow();
		}

		var connection = coupleService.connect(ACCEPTOR, key, code);
		assertThat(coupleService.connect(ACCEPTOR, key, code)).isEqualTo(connection);
		assertThat(countRelationshipsFor(ACCEPTOR)).isOne();
	}

	@Test
	void disconnectsForBothMembersAndKeepsTheSameGracePeriodOnRetries() {
		long coupleId = createCouple(ISSUER, ACCEPTOR);
		UUID key = UUID.randomUUID();
		var response = disconnectionService.disconnect(ISSUER, coupleId, key, false);

		assertThat(response.coupleId()).isEqualTo(coupleId);
		assertThat(response.status()).isEqualTo(CoupleStatus.GRACE);
		assertThat(response.disconnectedAt()).isEqualTo(NOW);
		assertThat(response.graceEndsAt()).isEqualTo(NOW.plus(Duration.ofHours(30 * 24)));
		for (long memberId : List.of(ISSUER, ACCEPTOR)) {
			var couple = coupleService.getMyCouple(memberId).couple();
			assertThat(couple.disconnectedAt()).isEqualTo(response.disconnectedAt());
			assertThat(couple.graceEndsAt()).isEqualTo(response.graceEndsAt());
			assertThat(couple.remainingGraceDays()).isEqualTo(30);
			assertThat(couple.sharedReadOnly()).isTrue();
			assertThat(couple.canReconnect()).isTrue();
		}

		given(clock.instant()).willReturn(NOW.plus(Duration.ofHours(1)));
		assertThat(disconnectionService.disconnect(ISSUER, coupleId, key, false)).isEqualTo(response);
		assertThat(disconnectionService.disconnect(ISSUER, coupleId, UUID.randomUUID(), false)).isEqualTo(response);
		assertThat(disconnectionService.disconnect(ACCEPTOR, coupleId, key, false)).isEqualTo(response);

		given(clock.instant()).willReturn(response.graceEndsAt().minusSeconds(1));
		UUID lateKey = UUID.randomUUID();
		assertThat(disconnectionService.disconnect(ISSUER, coupleId, lateKey, false)).isEqualTo(response);
		given(clock.instant()).willReturn(response.graceEndsAt());
		assertThat(coupleService.getMyCouple(ISSUER).couple()).isNull();
		assertThat(coupleService.getMyCouple(ACCEPTOR).couple()).isNull();
		assertThat(disconnectionService.disconnect(ISSUER, coupleId, lateKey, false)).isEqualTo(response);
		assertBusinessError("COUPLE-0409-02", () -> disconnectionService.disconnect(ISSUER, coupleId, key, false));
	}

	@Test
	void checksCurrentAccessAndRejectsNonemptyBodiesWithoutCachingFailures() {
		long coupleId = createCouple(ISSUER, ACCEPTOR);
		UUID key = UUID.randomUUID();
		assertBusinessError("COMMON-0401", () -> disconnectionService.disconnect(99L, coupleId, key, false));
		assertBusinessError("COUPLE-0404", () -> disconnectionService.disconnect(ISSUER, Long.MAX_VALUE, key, false));
		assertBusinessError("COUPLE-0404", () -> disconnectionService.disconnect(THIRD_MEMBER, coupleId, key, false));
		assertBusinessError("COMMON-0400", () -> disconnectionService.disconnect(ISSUER, coupleId, key, true));
		assertThat(coupleService.getMyCouple(ISSUER).couple().status()).isEqualTo(CoupleStatus.CONNECTED);
		assertThat(countDisconnectionReplays(coupleId, key)).isZero();

		var response = disconnectionService.disconnect(ISSUER, coupleId, key, false);
		assertBusinessError("COUPLE-0409-03", () -> disconnectionService.disconnect(ISSUER, coupleId, key, true));
		assertBusinessError("COUPLE-0404", () -> disconnectionService.disconnect(THIRD_MEMBER, coupleId, key, false));
		assertThat(disconnectionService.disconnect(ISSUER, coupleId, key, false)).isEqualTo(response);
	}

	@Test
	void scopesReplayToTheRequestedCoupleAndReturnsTheStoredResultForAtLeast24Hours() {
		long coupleId = createCouple(ISSUER, ACCEPTOR);
		UUID key = UUID.randomUUID();
		var response = disconnectionService.disconnect(ISSUER, coupleId, key, false);

		// Reconnecting may clear the live grace fields before a delayed retry arrives.
		jdbc.update("update couples set status = 'CONNECTED', disconnected_at = null, grace_ends_at = null where id = ?",
			coupleId);
		given(clock.instant()).willReturn(NOW.plus(Duration.ofHours(24)).minusNanos(1_000));
		assertThat(disconnectionService.disconnect(ISSUER, coupleId, key, false)).isEqualTo(response);
		assertThat(couples.findById(coupleId).orElseThrow().getStatus()).isEqualTo(CoupleStatus.CONNECTED);

		jdbc.update("update couples set status = 'ENDED' where id = ?", coupleId);
		assertBusinessError("COUPLE-0409-02",
			() -> disconnectionService.disconnect(ISSUER, coupleId, UUID.randomUUID(), false));
		long anotherCoupleId = createCouple(ISSUER, THIRD_MEMBER);
		assertThat(disconnectionService.disconnect(ISSUER, anotherCoupleId, key, false).coupleId())
			.isEqualTo(anotherCoupleId);
	}

	@Test
	void rejectsAnOverlappingFirstDisconnectionBeforeAnyReplayIsStored() throws Exception {
		long coupleId = createCouple(ISSUER, ACCEPTOR);
		UUID key = UUID.randomUUID();
		var coupleLocked = new CountDownLatch(1);
		var releaseCouple = new CountDownLatch(1);
		var firstRequestLocked = new CountDownLatch(1);
		doAnswer(invocation -> {
			invocation.callRealMethod();
			firstRequestLocked.countDown();
			return null;
		}).when(requestLock).acquireDisconnection(ISSUER, coupleId, key);

		assertThat(countDisconnectionReplays(coupleId, key)).isZero();
		try (var executor = Executors.newFixedThreadPool(3)) {
			try {
				var blocker = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
					jdbc.queryForObject("select id from couples where id = ? for update", Long.class, coupleId);
					coupleLocked.countDown();
					awaitRelease(releaseCouple);
				}));
				assertThat(coupleLocked.await(5, TimeUnit.SECONDS)).isTrue();
				var first = executor.submit(() -> disconnectionService.disconnect(ISSUER, coupleId, key, false));
				assertThat(firstRequestLocked.await(5, TimeUnit.SECONDS)).isTrue();

				executor.submit(() -> assertBusinessError("COUPLE-0409-04",
					() -> disconnectionService.disconnect(ISSUER, coupleId, key, false))).get(5, TimeUnit.SECONDS);
				assertThat(first.isDone()).isFalse();
				assertThat(countDisconnectionReplays(coupleId, key)).isZero();

				releaseCouple.countDown();
				blocker.get(5, TimeUnit.SECONDS);
				var response = first.get(5, TimeUnit.SECONDS);
				assertThat(response.status()).isEqualTo(CoupleStatus.GRACE);
				assertThat(countDisconnectionReplays(coupleId, key)).isOne();
				assertThat(disconnectionService.disconnect(ISSUER, coupleId, key, false)).isEqualTo(response);
			} finally {
				releaseCouple.countDown();
			}
		}
	}

	@Test
	void reconnectsJustBeforeGraceEndsAndRestoresSharedWriteAccess() {
		long coupleId = createCouple(ISSUER, ACCEPTOR);
		Instant connectedAt = couples.findById(coupleId).orElseThrow().getConnectedAt();
		Instant graceEndsAt = disconnect(coupleId);
		UUID key = UUID.randomUUID();
		var transaction = new TransactionTemplate(transactionManager);
		assertBusinessError("COUPLE-0409-01", () -> transaction.executeWithoutResult(status ->
			access.requireSharedWriteAccess(ACCEPTOR, coupleId)));

		given(clock.instant()).willReturn(graceEndsAt.minusSeconds(1));
		String code = coupleService.issueInvitation(ISSUER, RECONNECT).invitationCode();
		given(clock.instant()).willReturn(graceEndsAt.minusNanos(1_000));
		var response = coupleService.reconnect(ACCEPTOR, coupleId, key, code);
		assertThat(response)
			.isEqualTo(new ConnectionResponse(coupleId, CoupleStatus.CONNECTED));

		for (long memberId : List.of(ISSUER, ACCEPTOR)) {
			var couple = coupleService.getMyCouple(memberId).couple();
			assertThat(couple.coupleId()).isEqualTo(coupleId);
			assertThat(couple.status()).isEqualTo(CoupleStatus.CONNECTED);
			assertThat(couple.connectedAt()).isEqualTo(connectedAt);
			assertThat(couple.disconnectedAt()).isNull();
			assertThat(couple.graceEndsAt()).isNull();
			assertThat(couple.remainingGraceDays()).isNull();
			assertThat(couple.sharedReadOnly()).isFalse();
			assertThat(couple.canReconnect()).isFalse();
			transaction.executeWithoutResult(status -> access.requireSharedWriteAccess(memberId, coupleId));
		}
		assertThat(countRelationshipsFor(ACCEPTOR)).isOne();
		given(clock.instant()).willReturn(graceEndsAt);
		assertThat(coupleService.reconnect(ACCEPTOR, coupleId, key, code)).isEqualTo(response);
	}

	@Test
	void rejectsReconnectionAtTheExactGraceDeadline() {
		long coupleId = createCouple(ISSUER, ACCEPTOR);
		Instant graceEndsAt = disconnect(coupleId);
		given(clock.instant()).willReturn(graceEndsAt.minusSeconds(1));
		var invitation = coupleService.issueInvitation(ISSUER, RECONNECT);
		assertThat(invitation.expiresAt()).isEqualTo(graceEndsAt);

		given(clock.instant()).willReturn(graceEndsAt);
		assertBusinessError("COUPLE-0410",
			() -> coupleService.reconnect(ACCEPTOR, coupleId, UUID.randomUUID(), invitation.invitationCode()));
		assertThat(couples.findById(coupleId).orElseThrow().getStatus()).isEqualTo(CoupleStatus.GRACE);
	}

	@Test
	void keepsAReconnectionConfirmedBeforeTheDeadlineWhenExpirationWaitsForItsLock() throws Exception {
		long coupleId = createCouple(ISSUER, ACCEPTOR);
		Instant graceEndsAt = disconnect(coupleId);
		var currentTime = new AtomicReference<>(graceEndsAt.minusNanos(1_000));
		var confirmingThread = new AtomicReference<Thread>();
		var reconnectionConfirmed = new CountDownLatch(1);
		var releaseReconnection = new CountDownLatch(1);
		var expirationStarted = new CountDownLatch(1);
		given(clock.instant()).willAnswer(invocation -> {
			Instant sampledTime = currentTime.get();
			if (confirmingThread.compareAndSet(Thread.currentThread(), null)) {
				reconnectionConfirmed.countDown();
				awaitRelease(releaseReconnection);
			}
			return sampledTime;
		});
		String code = coupleService.issueInvitation(ISSUER, RECONNECT).invitationCode();
		CoupleAccessService accessSpy = AopTestUtils.getUltimateTargetObject(access);
		doAnswer(invocation -> {
			Couple locked = (Couple) invocation.callRealMethod();
			confirmingThread.set(Thread.currentThread());
			return locked;
		}).when(accessSpy).requireMemberCoupleForUpdate(ACCEPTOR, coupleId);

		try (var executor = Executors.newFixedThreadPool(2)) {
			try {
				var reconnection = executor.submit(() -> coupleService.reconnect(ACCEPTOR, coupleId, UUID.randomUUID(), code));
				assertThat(reconnectionConfirmed.await(5, TimeUnit.SECONDS)).isTrue();
				currentTime.set(graceEndsAt);
				var expiration = executor.submit(() -> {
					expirationStarted.countDown();
					return graceExpiration.endGrace(coupleId);
				});
				assertThat(expirationStarted.await(5, TimeUnit.SECONDS)).isTrue();
				assertThatThrownBy(() -> expiration.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

				releaseReconnection.countDown();
				assertThat(reconnection.get(5, TimeUnit.SECONDS))
					.isEqualTo(new ConnectionResponse(coupleId, CoupleStatus.CONNECTED));
				assertThat(expiration.get(5, TimeUnit.SECONDS)).isFalse();
			} finally {
				releaseReconnection.countDown();
			}
		}
		assertThat(couples.findById(coupleId).orElseThrow().getStatus()).isEqualTo(CoupleStatus.CONNECTED);
	}

	@Test
	void replaysTheOriginalResultWithoutReconnectingAgainAfterAnotherDisconnection() {
		long coupleId = createCouple(ISSUER, ACCEPTOR);
		disconnect(coupleId);
		String code = coupleService.issueInvitation(ISSUER, RECONNECT).invitationCode();
		String otherCode = coupleService.issueInvitation(ACCEPTOR, RECONNECT).invitationCode();
		UUID key = UUID.randomUUID();
		Instant reconnectedAt = NOW.plus(Duration.ofHours(1));
		given(clock.instant()).willReturn(reconnectedAt);
		var response = coupleService.reconnect(ACCEPTOR, coupleId, key, code);
		disconnect(coupleId);

		assertBusinessError("INVITATION-0409",
			() -> coupleService.reconnect(ACCEPTOR, coupleId, UUID.randomUUID(), code));
		assertBusinessError("INVITATION-0409",
			() -> coupleService.reconnect(ISSUER, coupleId, UUID.randomUUID(), otherCode));
		assertBusinessError("COUPLE-0409-03", () -> coupleService.reconnect(ACCEPTOR, coupleId, key, otherCode));
		assertBusinessError("COUPLE-0404", () -> coupleService.reconnect(THIRD_MEMBER, coupleId, key, code));

		given(clock.instant()).willReturn(reconnectedAt.plus(Duration.ofHours(24)).minusNanos(1_000));
		String normalizedCode = " " + code.replace("-", "").toLowerCase(Locale.ROOT) + " ";
		assertThat(coupleService.reconnect(ACCEPTOR, coupleId, key, normalizedCode)).isEqualTo(response);
		assertThat(coupleService.getMyCouple(ACCEPTOR).couple().status()).isEqualTo(CoupleStatus.GRACE);
	}

	@Test
	void scopesReplaysToTheRequestedCouple() {
		long firstCoupleId = createCouple(ISSUER, ACCEPTOR);
		disconnect(firstCoupleId);
		UUID key = UUID.randomUUID();
		String firstCode = coupleService.issueInvitation(ISSUER, RECONNECT).invitationCode();
		coupleService.reconnect(ACCEPTOR, firstCoupleId, key, firstCode);
		jdbc.update("update couples set status = 'ENDED' where id = ?", firstCoupleId);

		long secondCoupleId = createCouple(ISSUER, ACCEPTOR);
		disconnect(secondCoupleId);
		String secondCode = coupleService.issueInvitation(ISSUER, RECONNECT).invitationCode();
		assertThat(coupleService.reconnect(ACCEPTOR, secondCoupleId, key, secondCode))
			.isEqualTo(new ConnectionResponse(secondCoupleId, CoupleStatus.CONNECTED));
		assertBusinessError("COUPLE-0404", () -> coupleService.reconnect(ACCEPTOR, firstCoupleId, key, firstCode));
	}

	@Test
	void rejectsCodesFromTheRequesterOrAnotherRelationshipAndOtherPurposes() {
		String connectCode = coupleService.issueInvitation(ISSUER, CONNECT).invitationCode();
		long previousCoupleId = createCouple(ISSUER, ACCEPTOR);
		disconnect(previousCoupleId);
		String previousCode = coupleService.issueInvitation(ISSUER, RECONNECT).invitationCode();
		jdbc.update("update couples set status = 'ENDED' where id = ?", previousCoupleId);
		long coupleId = createCouple(ISSUER, ACCEPTOR);
		disconnect(coupleId);
		String ownCode = coupleService.issueInvitation(ACCEPTOR, RECONNECT).invitationCode();

		assertBusinessError("COMMON-0401",
			() -> coupleService.reconnect(99L, coupleId, UUID.randomUUID(), previousCode));
		assertBusinessError("COUPLE-0404",
			() -> coupleService.reconnect(THIRD_MEMBER, coupleId, UUID.randomUUID(), previousCode));
		assertBusinessError("COUPLE-0404",
			() -> coupleService.reconnect(ACCEPTOR, Long.MAX_VALUE, UUID.randomUUID(), previousCode));
		for (String invalidCode : List.of(ownCode, previousCode, connectCode)) {
			assertBusinessError("COUPLE-0409-02",
				() -> coupleService.reconnect(ACCEPTOR, coupleId, UUID.randomUUID(), invalidCode));
		}
		assertThat(couples.findById(coupleId).orElseThrow().getStatus()).isEqualTo(CoupleStatus.GRACE);
	}

	@Test
	void rejectsRevokedAndExpiredCodesAndKeepsTheKeyAvailableAfterFailure() {
		long coupleId = createCouple(ISSUER, ACCEPTOR);
		disconnect(coupleId);
		String revokedCode = coupleService.issueInvitation(ISSUER, RECONNECT).invitationCode();
		var expiringInvitation = coupleService.issueInvitation(ISSUER, RECONNECT);
		UUID key = UUID.randomUUID();
		assertBusinessError("INVITATION-0409", () -> coupleService.reconnect(ACCEPTOR, coupleId, key, revokedCode));

		given(clock.instant()).willReturn(expiringInvitation.expiresAt());
		assertBusinessError("INVITATION-0410",
			() -> coupleService.reconnect(ACCEPTOR, coupleId, key, expiringInvitation.invitationCode()));
		String code = coupleService.issueInvitation(ISSUER, RECONNECT).invitationCode();
		long conflictingCoupleId = createCouple(THIRD_MEMBER, ACCEPTOR);
		assertBusinessError("COUPLE-0409-02", () -> coupleService.reconnect(ACCEPTOR, coupleId, key, code));
		jdbc.update("update couples set status = 'ENDED' where id = ?", conflictingCoupleId);

		assertThat(coupleService.reconnect(ACCEPTOR, coupleId, key, code))
			.isEqualTo(new ConnectionResponse(coupleId, CoupleStatus.CONNECTED));
	}

	@Test
	void rejectsAnOverlappingReconnectionWithTheSameKey() throws Exception {
		long coupleId = createCouple(ISSUER, ACCEPTOR);
		disconnect(coupleId);
		String code = coupleService.issueInvitation(ISSUER, RECONNECT).invitationCode();
		UUID key = UUID.randomUUID();
		var firstRequestLocked = new CountDownLatch(1);
		var releaseFirstRequest = new CountDownLatch(1);
		doAnswer(invocation -> {
			invocation.callRealMethod();
			firstRequestLocked.countDown();
			awaitRelease(releaseFirstRequest);
			return null;
		}).when(requestLock).acquireReconnection(ACCEPTOR, coupleId, key);

		try (var executor = Executors.newFixedThreadPool(2)) {
			try {
				var first = executor.submit(() -> coupleService.reconnect(ACCEPTOR, coupleId, key, code));
				assertThat(firstRequestLocked.await(5, TimeUnit.SECONDS)).isTrue();
				executor.submit(() -> assertBusinessError("COUPLE-0409-04",
					() -> coupleService.reconnect(ACCEPTOR, coupleId, key, code))).get(5, TimeUnit.SECONDS);
				releaseFirstRequest.countDown();
				var response = first.get(5, TimeUnit.SECONDS);
				assertThat(response).isEqualTo(new ConnectionResponse(coupleId, CoupleStatus.CONNECTED));
				assertThat(coupleService.reconnect(ACCEPTOR, coupleId, key, code)).isEqualTo(response);
			} finally {
				releaseFirstRequest.countDown();
			}
		}
	}

	private Instant disconnect(long coupleId) {
		return disconnectionService.disconnect(ISSUER, coupleId, UUID.randomUUID(), false).graceEndsAt();
	}

	private int countDisconnectionReplays(long coupleId, UUID key) {
		return jdbc.queryForObject("""
			select count(*) from couple_disconnection_replays
			where member_id = ? and couple_id = ? and request_key = ?
			""", Integer.class, ISSUER, coupleId, key.toString());
	}

	private long createCouple(long firstMember, long secondMember) {
		return couples.save(Couple.connect(firstMember, secondMember, NOW.minus(Duration.ofDays(1)))).getId();
	}

	private int countRelationshipsFor(long memberId) {
		return jdbc.queryForObject("select count(*) from couples where first_member_id = ? or second_member_id = ?",
			Integer.class, memberId, memberId);
	}

	private static void assertBusinessError(String expectedCode, Runnable action) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
			exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo(expectedCode));
	}

	private static void awaitRelease(CountDownLatch release) {
		try {
			assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}

	private static List<ConnectionAttempt> runTogether(
			Supplier<ConnectionResponse> first, Supplier<ConnectionResponse> second) throws Exception {
		var executor = Executors.newFixedThreadPool(2);
		var ready = new CountDownLatch(2);
		var start = new CountDownLatch(1);
		try {
			var firstAttempt = executor.submit(() -> attemptConnection(first, ready, start));
			var secondAttempt = executor.submit(() -> attemptConnection(second, ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			return List.of(firstAttempt.get(10, TimeUnit.SECONDS), secondAttempt.get(10, TimeUnit.SECONDS));
		} finally {
			executor.shutdownNow();
		}
	}

	private static ConnectionAttempt attemptConnection(
			Supplier<ConnectionResponse> connect, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
		ready.countDown();
		start.await();
		try {
			return new ConnectionAttempt(connect.get(), null);
		} catch (BusinessException exception) {
			return new ConnectionAttempt(null, exception.getErrorCode().getCode());
		}
	}

	private record ConnectionAttempt(ConnectionResponse response, String errorCode) {
	}
}
