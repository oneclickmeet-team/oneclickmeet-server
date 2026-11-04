package com.hsu.oneclickmeet.domain.couple.service;

import static com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus.CONNECTED;
import static com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus.GRACE;
import static com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode.IDEMPOTENCY_MISMATCH;
import static com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode.REQUEST_IN_PROGRESS;
import static com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode.STATE_CONFLICT;
import static com.hsu.oneclickmeet.global.exception.CommonErrorCode.BAD_REQUEST;
import static com.hsu.oneclickmeet.global.exception.CommonErrorCode.UNAUTHORIZED;

import com.hsu.oneclickmeet.domain.couple.dto.response.DisconnectionResponse;
import com.hsu.oneclickmeet.domain.couple.entity.CoupleDisconnectionReplay;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleDisconnectionReplayRepository;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleMemberRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CoupleDisconnectionService {

	private static final Duration REPLAY_LIFETIME = Duration.ofHours(24);
	private static final int MYSQL_NOWAIT_LOCK_CONFLICT = 3572;

	private final CoupleAccessService access;
	private final CoupleDisconnectionReplayRepository replays;
	private final CoupleMemberRepository members;
	private final Clock clock;
	private final TransactionTemplate writeTransaction;

	public CoupleDisconnectionService(CoupleAccessService access, CoupleDisconnectionReplayRepository replays,
			CoupleMemberRepository members, @Qualifier("coupleClock") Clock clock,
			PlatformTransactionManager transactionManager) {
		this.access = access;
		this.replays = replays;
		this.members = members;
		this.clock = clock;
		this.writeTransaction = new TransactionTemplate(transactionManager);
		this.writeTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
		this.writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public DisconnectionResponse disconnect(long memberId, long coupleId, UUID idempotencyKey, boolean hasBody) {
		if (members.findDisplayName(memberId).isEmpty()) {
			throw new BusinessException(UNAUTHORIZED);
		}
		access.requireMemberCouple(memberId, coupleId);
		String key = idempotencyKey.toString();
		if (!replays.existsByMemberIdAndCoupleIdAndRequestKey(memberId, coupleId, key)) {
			if (hasBody) {
				throw new BusinessException(BAD_REQUEST);
			}
			// A committed lock row lets an overlapping request fail immediately with NOWAIT.
			writeTransaction.executeWithoutResult(transaction -> replays.prepare(memberId, coupleId, key));
		}
		return writeTransaction.execute(transaction -> disconnect(memberId, coupleId, key, hasBody));
	}

	private DisconnectionResponse disconnect(long memberId, long coupleId, String key, boolean hasBody) {
		CoupleDisconnectionReplay replay = lockReplay(memberId, coupleId, key);
		var couple = access.requireMemberCoupleForUpdate(memberId, coupleId);
		Instant now = now();
		if (replay.isReplayable(now)) {
			if (hasBody) {
				throw new BusinessException(IDEMPOTENCY_MISMATCH);
			}
			return new DisconnectionResponse(coupleId, GRACE, replay.getDisconnectedAt(), replay.getGraceEndsAt());
		}
		if (hasBody) {
			throw new BusinessException(BAD_REQUEST);
		}
		if (couple.getStatus() != CONNECTED && !couple.isInGrace(now)) {
			throw new BusinessException(STATE_CONFLICT);
		}
		if (couple.getStatus() == CONNECTED) {
			couple.disconnect(now);
		}
		replay.complete(couple, now().plus(REPLAY_LIFETIME));
		return new DisconnectionResponse(coupleId, GRACE, couple.getDisconnectedAt(), couple.getGraceEndsAt());
	}

	private CoupleDisconnectionReplay lockReplay(long memberId, long coupleId, String key) {
		try {
			return replays.lock(memberId, coupleId, key);
		} catch (DataAccessException exception) {
			for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
				if (cause instanceof SQLException sql && sql.getErrorCode() == MYSQL_NOWAIT_LOCK_CONFLICT) {
					throw new BusinessException(REQUEST_IN_PROGRESS);
				}
			}
			throw exception;
		}
	}

	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}
}
