package com.hsu.oneclickmeet.domain.course.repository;

import com.hsu.oneclickmeet.domain.course.exception.CourseErrorCode;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.jdbc.MysqlRequestLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CourseRequestLock {

	private final MysqlRequestLock requestLock;

	public void acquireSave(long memberId, UUID requestKey) {
		if (!requestLock.tryAcquire(":course-save:" + memberId + ":" + requestKey)) {
			throw new BusinessException(CourseErrorCode.SAVE_IN_PROGRESS);
		}
	}

	public void acquireUpdate(long memberId, long courseId, UUID requestKey) {
		if (!requestLock.tryAcquire(":course-save:update:" + memberId + ":" + courseId + ":" + requestKey)) {
			throw new BusinessException(CourseErrorCode.SAVE_IN_PROGRESS);
		}
	}

	public boolean tryAcquireShare(long memberId, long courseId, UUID requestKey) {
		return requestLock.tryAcquire(":course-share:" + memberId + ":" + courseId + ":" + requestKey);
	}
}
