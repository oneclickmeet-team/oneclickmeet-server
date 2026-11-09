package com.hsu.oneclickmeet.domain.member.service;

import com.hsu.oneclickmeet.domain.member.dto.response.MemberResponse;
import com.hsu.oneclickmeet.domain.member.exception.MemberErrorCode;
import com.hsu.oneclickmeet.domain.member.repository.MemberRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 로그인한 회원 본인의 계정 정보 조회·수정. */
@Service
public class MemberService {

	private final MemberRepository repository;
	private final TransactionTemplate transaction;

	public MemberService(MemberRepository repository, PlatformTransactionManager transactionManager) {
		this.repository = repository;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	public MemberResponse me(long memberId) {
		return transaction.execute(status -> repository.findById(memberId)
				.map(MemberResponse::from)
				.orElseThrow(() -> new BusinessException(MemberErrorCode.NOT_FOUND)));
	}

	public MemberResponse changeNickname(long memberId, String nickname) {
		return transaction.execute(status -> {
			var member = repository.findById(memberId)
					.orElseThrow(() -> new BusinessException(MemberErrorCode.NOT_FOUND));
			member.changeNickname(nickname);
			repository.saveAndFlush(member);
			return MemberResponse.from(member);
		});
	}
}
