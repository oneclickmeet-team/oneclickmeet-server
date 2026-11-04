package com.hsu.oneclickmeet.domain.couple.service;

import static com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus.CONNECTED;
import static com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus.GRACE;
import static com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode.IDEMPOTENCY_MISMATCH;
import static com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode.STATE_CONFLICT;
import static com.hsu.oneclickmeet.global.exception.CommonErrorCode.BAD_REQUEST;
import static com.hsu.oneclickmeet.global.exception.CommonErrorCode.UNAUTHORIZED;

import com.hsu.oneclickmeet.domain.couple.dto.response.DisconnectionResponse;
import com.hsu.oneclickmeet.domain.couple.entity.CoupleDisconnectionReplay;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleDisconnectionReplayRepository;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleMemberRepository;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRequestLock;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoupleDisconnectionService {

	private static final Duration REPLAY_LIFETIME = Duration.ofHours(24);

	private final CoupleAccessService access;
	private final CoupleDisconnectionReplayRepository replays;
	private final CoupleRequestLock requestLock;
	private final CoupleMemberRepository members;
	private final Clock clock;

	public CoupleDisconnectionService(CoupleAccessService access, CoupleDisconnectionReplayRepository replays,
			CoupleRequestLock requestLock, CoupleMemberRepository members,
			@Qualifier("coupleClock") Clock clock) {
		this.access = access;
		this.replays = replays;
		this.requestLock = requestLock;
		this.members = members;
		this.clock = clock;
	}

	@Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
	public DisconnectionResponse disconnect(long memberId, long coupleId, UUID idempotencyKey, boolean hasBody) {
		if (members.findDisplayName(memberId).isEmpty()) {
			throw new BusinessException(UNAUTHORIZED);
		}
		access.requireMemberCouple(memberId, coupleId);
		requestLock.acquireDisconnection(memberId, coupleId, idempotencyKey);
		String key = idempotencyKey.toString();
		CoupleDisconnectionReplay replay = replays.findByMemberIdAndCoupleIdAndRequestKey(memberId, coupleId, key)
			.orElseGet(() -> new CoupleDisconnectionReplay(memberId, coupleId, key));
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
		replays.save(replay);
		return new DisconnectionResponse(coupleId, GRACE, couple.getDisconnectedAt(), couple.getGraceEndsAt());
	}

	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}
}
