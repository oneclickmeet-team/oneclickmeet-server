package com.hsu.oneclickmeet.domain.couple.service;

import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.course.repository.SharedCourseRepository;
import com.hsu.oneclickmeet.domain.course.service.SharedCourseCopyService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CoupleGraceExpirationJob {

	private static final PageRequest BATCH = PageRequest.of(0, 100);
	private static final String DELAY = "${oneclickmeet.couple.grace-expiration-delay-ms:60000}";

	private final CoupleRepository couples;
	private final SharedCourseRepository sharedCourses;
	private final CoupleGraceExpirationService expiration;
	private final SharedCourseCopyService courseCopies;
	private final Clock clock;

	public CoupleGraceExpirationJob(CoupleRepository couples, SharedCourseRepository sharedCourses,
			CoupleGraceExpirationService expiration, SharedCourseCopyService courseCopies,
			@Qualifier("coupleClock") Clock clock) {
		this.couples = couples;
		this.sharedCourses = sharedCourses;
		this.expiration = expiration;
		this.courseCopies = courseCopies;
		this.clock = clock;
	}

	@Scheduled(initialDelayString = DELAY, fixedDelayString = DELAY)
	public void expireGraceAndCopyCourses() {
		endExpiredRelationships(clock.instant().truncatedTo(ChronoUnit.MICROS));
		copyPendingCourses();
	}

	private void endExpiredRelationships(Instant cutoff) {
		long afterId = 0;
		while (true) {
			var ids = couples.findExpiredGraceIds(cutoff, afterId, BATCH);
			if (ids.isEmpty()) {
				return;
			}
			for (long coupleId : ids) {
				try {
					expiration.endGrace(coupleId);
				} catch (RuntimeException failure) {
					log.error("커플 유예 종료 실패: coupleId={}", coupleId, failure);
				}
				afterId = coupleId;
			}
		}
	}

	private void copyPendingCourses() {
		long afterId = 0;
		while (true) {
			var candidates = sharedCourses.findPendingGraceCopies(afterId, BATCH);
			if (candidates.isEmpty()) {
				return;
			}
			for (var candidate : candidates) {
				long coupleId = candidate.getCoupleId();
				long sharedCourseId = candidate.getSharedCourseId();
				try {
					courseCopies.copyAfterGrace(coupleId, sharedCourseId);
				} catch (RuntimeException failure) {
					log.error("유예 종료 후 코스 사본 이전 실패: coupleId={}, sharedCourseId={}",
						coupleId, sharedCourseId, failure);
				}
				afterId = sharedCourseId;
			}
		}
	}
}
