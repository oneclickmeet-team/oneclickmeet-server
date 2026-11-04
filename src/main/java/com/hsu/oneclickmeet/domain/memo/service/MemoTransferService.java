package com.hsu.oneclickmeet.domain.memo.service;

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

	/**
	 * Relinks the recipient's existing memo to their new personal copy.
	 * The caller holds the source couple lock in a READ_COMMITTED transaction and supplies
	 * the recipient's fresh copy ID, or the same ID on retry, before closing shared access.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void copyForUnshare(long sharedCourseId, long memberId, long personalCourseId) {
		if (memos.existsByAuthorIdAndTargetTypeAndTargetId(memberId, MemoTargetType.COURSE, personalCourseId)) {
			return;
		}
		memos.moveToCopy(memberId, MemoTargetType.SHARED_COURSE, sharedCourseId, MemoTargetType.COURSE, personalCourseId);
	}
}
