package com.hsu.oneclickmeet.domain.couple.service;

import static com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode.COUPLE_NOT_FOUND;
import static com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode.GRACE_READ_ONLY;

import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoupleAccessService {

	private final CoupleRepository couples;
	private final Clock clock;

	public CoupleAccessService(CoupleRepository couples, @Qualifier("coupleClock") Clock clock) {
		this.couples = couples;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public void requireSharedReadAccess(long memberId, long coupleId) {
		Couple couple = couples.findById(coupleId)
			.filter(candidate -> candidate.isMember(memberId))
			.orElseThrow(() -> new BusinessException(COUPLE_NOT_FOUND));
		if (couple.getStatus() != CoupleStatus.CONNECTED && !couple.isInGrace(clock.instant())) {
			throw new BusinessException(COUPLE_NOT_FOUND);
		}
	}

	// 관계나 공동 기록을 조회하기 전에 호출하고 같은 트랜잭션에서 공동 기록을 변경한다.
	@Transactional(propagation = Propagation.MANDATORY)
	public void requireSharedWriteAccess(long memberId, long coupleId) {
		Couple couple = requireMemberCoupleForUpdate(memberId, coupleId);
		if (couple.getStatus() == CoupleStatus.CONNECTED) {
			return;
		}
		if (couple.isInGrace(clock.instant())) {
			throw new BusinessException(GRACE_READ_ONLY);
		}
		throw new BusinessException(COUPLE_NOT_FOUND);
	}

	@Transactional(readOnly = true)
	public void requireMemberCouple(long memberId, long coupleId) {
		couples.findMemberCoupleId(memberId, coupleId)
			.orElseThrow(() -> new BusinessException(COUPLE_NOT_FOUND));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Couple requireMemberCoupleForUpdate(long memberId, long coupleId) {
		return couples.findMemberCoupleForUpdate(memberId, coupleId)
			.orElseThrow(() -> new BusinessException(COUPLE_NOT_FOUND));
	}
}
