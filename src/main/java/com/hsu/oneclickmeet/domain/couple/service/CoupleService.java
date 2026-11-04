package com.hsu.oneclickmeet.domain.couple.service;

import static com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus.GRACE;
import static com.hsu.oneclickmeet.domain.couple.entity.InvitationPurpose.CONNECT;
import static com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode.IDEMPOTENCY_MISMATCH;
import static com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode.INVITATION_EXPIRED;
import static com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode.INVITATION_NOT_FOUND;
import static com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode.INVITATION_UNAVAILABLE;
import static com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode.REQUEST_IN_PROGRESS;
import static com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode.STATE_CONFLICT;
import static com.hsu.oneclickmeet.global.exception.CommonErrorCode.BAD_REQUEST;
import static com.hsu.oneclickmeet.global.exception.CommonErrorCode.UNAUTHORIZED;

import com.hsu.oneclickmeet.domain.couple.dto.response.ConnectionResponse;
import com.hsu.oneclickmeet.domain.couple.dto.response.CoupleResponse;
import com.hsu.oneclickmeet.domain.couple.dto.response.InvitationResponse;
import com.hsu.oneclickmeet.domain.couple.dto.response.MyCoupleResponse;
import com.hsu.oneclickmeet.domain.couple.dto.response.MyInvitationResponse;
import com.hsu.oneclickmeet.domain.couple.dto.response.PartnerResponse;
import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.entity.CoupleConnectionReplay;
import com.hsu.oneclickmeet.domain.couple.entity.CoupleInvitation;
import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;
import com.hsu.oneclickmeet.domain.couple.entity.InvitationPurpose;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleConnectionReplayRepository;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleInvitationRepository;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleMemberRepository;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CoupleService {

	private static final Duration INVITATION_LIFETIME = Duration.ofHours(24);
	private static final Duration REPLAY_LIFETIME = Duration.ofHours(24);
	private static final String CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	private static final int CODE_LENGTH = 7;
	private static final int MAX_CODE_ATTEMPTS = 5;
	private static final int MYSQL_NOWAIT_LOCK_CONFLICT = 3572;
	private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9]{7}");
	private static final SecureRandom RANDOM = new SecureRandom();

	private final CoupleRepository couples;
	private final CoupleInvitationRepository invitations;
	private final CoupleConnectionReplayRepository replays;
	private final CoupleMemberRepository members;
	private final Clock clock;
	private final TransactionTemplate writeTransaction;

	public CoupleService(CoupleRepository couples, CoupleInvitationRepository invitations,
			CoupleConnectionReplayRepository replays, CoupleMemberRepository members,
			@Qualifier("coupleClock") Clock clock, PlatformTransactionManager transactionManager) {
		this.couples = couples;
		this.invitations = invitations;
		this.replays = replays;
		this.members = members;
		this.clock = clock;
		this.writeTransaction = new TransactionTemplate(transactionManager);
		this.writeTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
		this.writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Transactional(readOnly = true)
	public MyCoupleResponse getMyCouple(long memberId) {
		requireMember(memberId);
		Instant now = now();
		return new MyCoupleResponse(couples.findCurrent(memberId, now)
				.map(couple -> toCoupleResponse(couple, memberId, now)).orElse(null));
	}

	public InvitationResponse issueInvitation(long memberId, InvitationPurpose purpose) {
		for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
			String code = generateCode();
			try {
				return writeTransaction.execute(transaction -> issueInvitation(memberId, purpose, code));
			} catch (DataIntegrityViolationException exception) {
				if (attempt == MAX_CODE_ATTEMPTS - 1 || !invitations.existsByCode(code)) {
					throw exception;
				}
			}
		}
		throw new IllegalStateException("초대 코드 발급 재시도를 소진했습니다.");
	}

	private InvitationResponse issueInvitation(long memberId, InvitationPurpose purpose, String code) {
		if (!members.lock(memberId)) {
			throw new BusinessException(UNAUTHORIZED);
		}
		Instant now = now();
		Couple current = couples.findCurrent(memberId, now).orElse(null);
		if (!allowsInvitation(current, purpose)) {
			throw new BusinessException(STATE_CONFLICT);
		}
		Long coupleId = null;
		Instant expiresAt = now.plus(INVITATION_LIFETIME);
		if (current != null) {
			coupleId = current.getId();
			if (expiresAt.isAfter(current.getGraceEndsAt())) {
				expiresAt = current.getGraceEndsAt();
			}
		}
		invitations.findPending(memberId, purpose).ifPresent(CoupleInvitation::revoke);
		return toInvitationResponse(invitations.saveAndFlush(
				CoupleInvitation.issue(memberId, purpose, code, coupleId, expiresAt)));
	}

	@Transactional(readOnly = true)
	public MyInvitationResponse getMyInvitation(long memberId, InvitationPurpose purpose) {
		requireMember(memberId);
		Instant now = now();
		Couple current = couples.findCurrent(memberId, now).orElse(null);
		if (!allowsInvitation(current, purpose)) {
			return new MyInvitationResponse(null);
		}
		Long coupleId = current == null ? null : current.getId();
		return new MyInvitationResponse(invitations.findActive(memberId, purpose, now)
				.filter(invitation -> Objects.equals(invitation.getCoupleId(), coupleId))
				.map(this::toInvitationResponse).orElse(null));
	}

	private boolean allowsInvitation(Couple current, InvitationPurpose purpose) {
		return purpose == CONNECT ? current == null : current != null && current.getStatus() == GRACE;
	}

	public ConnectionResponse connect(long memberId, UUID idempotencyKey, String invitationCode) {
		requireMember(memberId);
		String code = normalizeCode(invitationCode);
		String key = idempotencyKey.toString();
		// Commit the lock row first so other instances can use NOWAIT while the request runs.
		if (!replays.existsByMemberIdAndRequestKey(memberId, key)) {
			writeTransaction.executeWithoutResult(transaction -> replays.prepare(memberId, key));
		}
		return writeTransaction.execute(transaction -> connect(memberId, key, code));
	}

	private ConnectionResponse connect(long memberId, String key, String code) {
		CoupleConnectionReplay replay = lockReplay(memberId, key);
		if (replay.isReplayable(now())) {
			if (!code.equals(replay.getInvitationCode())) {
				throw new BusinessException(IDEMPOTENCY_MISMATCH);
			}
			return new ConnectionResponse(replay.getCoupleId(), CoupleStatus.CONNECTED);
		}
		long issuerId = invitations.findIssuerIdByCode(code)
				.orElseThrow(() -> new BusinessException(INVITATION_NOT_FOUND));
		if (issuerId == memberId) {
			throw new BusinessException(STATE_CONFLICT);
		}
		lockMembers(memberId, issuerId);
		CoupleInvitation invitation = invitations.findByCode(code)
				.orElseThrow(() -> new BusinessException(INVITATION_NOT_FOUND));
		Instant now = now();
		if (invitation.isUsedOrRevoked()) {
			throw new BusinessException(INVITATION_UNAVAILABLE);
		}
		if (!invitation.isAvailable(now)) {
			throw new BusinessException(INVITATION_EXPIRED);
		}
		if (invitation.getPurpose() != CONNECT || couples.findCurrent(memberId, now).isPresent()
				|| couples.findCurrent(issuerId, now).isPresent()) {
			throw new BusinessException(STATE_CONFLICT);
		}

		Couple couple = couples.save(Couple.connect(issuerId, memberId, now));
		invitation.consume();
		replay.complete(code, couple.getId(), now().plus(REPLAY_LIFETIME));
		return new ConnectionResponse(couple.getId(), couple.getStatus());
	}

	private CoupleConnectionReplay lockReplay(long memberId, String key) {
		try {
			return replays.lock(memberId, key);
		} catch (DataAccessException exception) {
			for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
				if (cause instanceof SQLException sql && sql.getErrorCode() == MYSQL_NOWAIT_LOCK_CONFLICT) {
					throw new BusinessException(REQUEST_IN_PROGRESS);
				}
			}
			throw exception;
		}
	}

	private void lockMembers(long memberId, long issuerId) {
		// Use the same lock order even when two people accept each other's codes.
		boolean firstExists = members.lock(Math.min(memberId, issuerId));
		boolean secondExists = members.lock(Math.max(memberId, issuerId));
		if (!(memberId < issuerId ? firstExists : secondExists)) {
			throw new BusinessException(UNAUTHORIZED);
		}
		if (!firstExists || !secondExists) {
			throw new BusinessException(INVITATION_NOT_FOUND);
		}
	}

	private void requireMember(long memberId) {
		if (members.findDisplayName(memberId).isEmpty()) {
			throw new BusinessException(UNAUTHORIZED);
		}
	}

	private CoupleResponse toCoupleResponse(Couple couple, long memberId, Instant now) {
		long partnerId = couple.partnerId(memberId);
		String name = members.findDisplayName(partnerId)
				.orElseThrow(() -> new IllegalStateException("현재 관계의 상대 회원이 존재하지 않습니다."));
		boolean inGrace = couple.getStatus() == GRACE;
		Integer remainingGraceDays = null;
		if (inGrace) {
			Duration remaining = Duration.between(now, couple.getGraceEndsAt());
			long days = remaining.toDays();
			remainingGraceDays = (int) (days + (remaining.minusDays(days).isZero() ? 0 : 1));
		}
		return new CoupleResponse(couple.getId(), couple.getStatus(), new PartnerResponse(partnerId, name),
				couple.getConnectedAt(), couple.getDisconnectedAt(), couple.getGraceEndsAt(), remainingGraceDays,
				inGrace, inGrace);
	}

	private InvitationResponse toInvitationResponse(CoupleInvitation invitation) {
		String code = invitation.getCode();
		return new InvitationResponse(invitation.getId(), invitation.getPurpose(),
				code.substring(0, 3) + "-" + code.substring(3), invitation.getCoupleId(), invitation.getExpiresAt());
	}

	private String generateCode() {
		StringBuilder code = new StringBuilder(CODE_LENGTH);
		for (int index = 0; index < CODE_LENGTH; index++) {
			code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
		}
		return code.toString();
	}

	private String normalizeCode(String invitationCode) {
		String code = invitationCode.strip().replace("-", "").toUpperCase(Locale.ROOT);
		if (!CODE_PATTERN.matcher(code).matches()) {
			throw new BusinessException(BAD_REQUEST);
		}
		return code;
	}

	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}
}
