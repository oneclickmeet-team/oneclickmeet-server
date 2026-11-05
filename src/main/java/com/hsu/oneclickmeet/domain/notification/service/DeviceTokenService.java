package com.hsu.oneclickmeet.domain.notification.service;

import com.hsu.oneclickmeet.domain.notification.entity.DevicePlatform;
import com.hsu.oneclickmeet.domain.notification.entity.DeviceToken;
import com.hsu.oneclickmeet.domain.notification.repository.DeviceTokenRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 푸시용 기기 토큰 등록·해제. 같은 토큰을 다시 등록해도 안전하고(멱등), 한 회원이 가질 수 있는 토큰은
 * {@value #MAX_TOKENS_PER_MEMBER}개까지이며 넘으면 가장 오래된 것부터 지운다.
 */
@Service
public class DeviceTokenService {

	static final int MAX_TOKENS_PER_MEMBER = 10;

	private final DeviceTokenRepository repository;
	private final TransactionTemplate transaction;

	public DeviceTokenService(DeviceTokenRepository repository, PlatformTransactionManager transactionManager) {
		this.repository = repository;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	public void register(long memberId, DevicePlatform platform, String token) {
		String clean = token.trim();
		try {
			transaction.executeWithoutResult(status -> upsert(memberId, platform, clean));
		} catch (DataIntegrityViolationException exception) {
			// 같은 토큰이 동시에 처음 등록된 경우: 먼저 생긴 행을 현재 회원으로 맞춘다.
			transaction.executeWithoutResult(status -> upsert(memberId, platform, clean));
		}
	}

	/** 본인의 토큰을 지운다. 없거나 다른 회원의 토큰이어도 성공으로 본다. */
	public void unregister(long memberId, String token) {
		transaction.executeWithoutResult(status -> repository.deleteByTokenAndMemberId(token.trim(), memberId));
	}

	private void upsert(long memberId, DevicePlatform platform, String token) {
		DeviceToken existing = repository.findByToken(token).orElse(null);
		if (existing != null) {
			existing.reassign(memberId, platform);
			repository.saveAndFlush(existing);
		} else {
			repository.saveAndFlush(DeviceToken.register(memberId, platform, token));
		}
		trimOldTokens(memberId);
	}

	private void trimOldTokens(long memberId) {
		long excess = repository.countByMemberId(memberId) - MAX_TOKENS_PER_MEMBER;
		if (excess > 0) {
			List<DeviceToken> newestFirst = repository.findByMemberIdOrderByUpdatedAtDescIdDesc(memberId);
			repository.deleteAll(newestFirst.subList(MAX_TOKENS_PER_MEMBER, newestFirst.size()));
		}
	}
}
