package com.hsu.oneclickmeet.domain.couple.repository;

import com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.persistence.MySqlTransactionLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CoupleRequestLock {

	private final MySqlTransactionLock locks;

	public void acquireDisconnection(long memberId, long coupleId, UUID requestKey) {
		acquire(":couple-disconnection:" + memberId + ":" + coupleId + ":" + requestKey);
	}

	public void acquireReconnection(long memberId, long coupleId, UUID requestKey) {
		acquire(":couple-reconnection:" + memberId + ":" + coupleId + ":" + requestKey);
	}

	private void acquire(String key) {
		if (!locks.tryAcquire(key)) {
			throw new BusinessException(CoupleErrorCode.REQUEST_IN_PROGRESS);
		}
	}
}
