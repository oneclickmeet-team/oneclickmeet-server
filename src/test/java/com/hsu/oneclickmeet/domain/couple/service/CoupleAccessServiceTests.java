package com.hsu.oneclickmeet.domain.couple.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("test")
@SpringBootTest
class CoupleAccessServiceTests {

	private static final long FIRST_MEMBER = 11001L;
	private static final long SECOND_MEMBER = 11002L;
	private static final long OTHER_MEMBER = 11003L;
	private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");

	@Autowired
	private CoupleAccessService access;

	@Autowired
	private CoupleRepository couples;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@MockitoBean(name = "coupleClock")
	private Clock clock;

	private long coupleId;

	@BeforeEach
	void connectMembers() {
		given(clock.instant()).willReturn(NOW);
		coupleId = couples.save(Couple.connect(FIRST_MEMBER, SECOND_MEMBER, NOW.minusSeconds(1))).getId();
	}

	@AfterEach
	void removeCouple() {
		couples.deleteById(coupleId);
	}

	@Test
	void allowsOnlyTheTwoMembersToReadAndWriteConnectedRecords() {
		for (long memberId : new long[] {FIRST_MEMBER, SECOND_MEMBER}) {
			assertThatCode(() -> access.requireSharedReadAccess(memberId, coupleId)).doesNotThrowAnyException();
			assertThatCode(() -> requireSharedWriteAccess(memberId)).doesNotThrowAnyException();
		}

		assertBusinessError("COUPLE-0404", () -> access.requireMemberCouple(OTHER_MEMBER, coupleId));
		assertBusinessError("COUPLE-0404", () -> access.requireSharedReadAccess(OTHER_MEMBER, coupleId));
		assertBusinessError("COUPLE-0404", () -> requireSharedWriteAccess(OTHER_MEMBER));
	}

	@Test
	void allowsBothMembersToReadUntilGraceEndsButRejectsSharedChanges() {
		Instant graceEndsAt = NOW.plus(Duration.ofHours(30 * 24));
		new TransactionTemplate(transactionManager).executeWithoutResult(transaction ->
			access.requireMemberCoupleForUpdate(FIRST_MEMBER, coupleId).disconnect(NOW));
		given(clock.instant()).willReturn(graceEndsAt.minus(1, ChronoUnit.MICROS));

		for (long memberId : new long[] {FIRST_MEMBER, SECOND_MEMBER}) {
			assertThatCode(() -> access.requireSharedReadAccess(memberId, coupleId)).doesNotThrowAnyException();
			assertBusinessError("COUPLE-0409-01", () -> requireSharedWriteAccess(memberId));
		}
		assertBusinessError("COUPLE-0404", () -> access.requireSharedReadAccess(OTHER_MEMBER, coupleId));

		given(clock.instant()).willReturn(graceEndsAt);

		for (long memberId : new long[] {FIRST_MEMBER, SECOND_MEMBER}) {
			assertBusinessError("COUPLE-0404", () -> access.requireSharedReadAccess(memberId, coupleId));
			assertBusinessError("COUPLE-0404", () -> requireSharedWriteAccess(memberId));
		}
	}

	@Test
	void requiresTheSharedWriteToKeepAnOpenTransaction() {
		assertThatThrownBy(() -> access.requireSharedWriteAccess(FIRST_MEMBER, coupleId))
			.isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	void holdsTheCoupleRowLockUntilTheSharedWriteTransactionEnds() {
		var separateTransaction = new TransactionTemplate(transactionManager);
		separateTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
			access.requireSharedWriteAccess(FIRST_MEMBER, coupleId);

			assertThatThrownBy(() -> separateTransaction.execute(transactionStatus -> lockCoupleWithoutWaiting()))
				.isInstanceOf(DataAccessException.class)
				.hasRootCauseInstanceOf(SQLException.class)
				.rootCause()
				.isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getErrorCode()).isEqualTo(3572));
		});
		Long lockedId = separateTransaction.execute(transactionStatus -> lockCoupleWithoutWaiting());
		assertThat(lockedId).isEqualTo(coupleId);
	}

	private Long lockCoupleWithoutWaiting() {
		return jdbc.queryForObject("select id from couples where id = ? for update nowait", Long.class, coupleId);
	}

	private void requireSharedWriteAccess(long memberId) {
		new TransactionTemplate(transactionManager).executeWithoutResult(transaction ->
			access.requireSharedWriteAccess(memberId, coupleId));
	}

	private static void assertBusinessError(String code, Runnable action) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
			error -> assertThat(error.getErrorCode().getCode()).isEqualTo(code));
	}
}
