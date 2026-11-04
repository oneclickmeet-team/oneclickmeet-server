package com.hsu.oneclickmeet.domain.memo.service;

import com.hsu.oneclickmeet.domain.memo.entity.Memo;
import com.hsu.oneclickmeet.domain.memo.entity.MemoTargetType;
import com.hsu.oneclickmeet.domain.memo.repository.MemoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemoTransferService {

	private final MemoRepository memos;

	@Transactional(propagation = Propagation.MANDATORY)
	public void copyForUnshare(long sharedCourseId, long memberId, long personalCourseId) {
		memos.findByAuthorIdAndTargetTypeAndTargetId(memberId, MemoTargetType.SHARED_COURSE, sharedCourseId)
				.ifPresent(memo -> memos.save(new Memo(memberId, MemoTargetType.COURSE, personalCourseId, memo.getContent())));
	}
}
