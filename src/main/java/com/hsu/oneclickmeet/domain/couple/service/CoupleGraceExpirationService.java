package com.hsu.oneclickmeet.domain.couple.service;

import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoupleGraceExpirationService {

	private final CoupleRepository couples;
	private final Clock clock;

	public CoupleGraceExpirationService(CoupleRepository couples, @Qualifier("coupleClock") Clock clock) {
		this.couples = couples;
		this.clock = clock;
	}

	@Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
	public boolean endGrace(long coupleId) {
		return couples.findByIdForUpdate(coupleId)
			.map(couple -> couple.endGrace(clock.instant().truncatedTo(ChronoUnit.MICROS)))
			.orElse(false);
	}
}
