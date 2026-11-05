package com.hsu.oneclickmeet.domain.activity.service;

import com.hsu.oneclickmeet.domain.activity.dto.response.ActivityListResponse;
import com.hsu.oneclickmeet.domain.activity.dto.response.ActivityResponse;
import com.hsu.oneclickmeet.domain.activity.entity.Activity;
import com.hsu.oneclickmeet.domain.activity.repository.ActivityRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/** 회원 본인의 활동 기록 조회. */
@Service
public class ActivityService {

	private final ActivityRepository repository;
	private final TransactionTemplate transaction;

	public ActivityService(ActivityRepository repository, PlatformTransactionManager transactionManager) {
		this.repository = repository;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	public ActivityListResponse list(long memberId, String cursor, int size) {
		Long cursorId = ActivityCursor.decode(cursor, memberId);
		List<Activity> found = transaction.execute(
				status -> repository.findPage(memberId, cursorId, PageRequest.ofSize(size + 1)));
		boolean hasNext = found.size() > size;
		List<Activity> page = hasNext ? found.subList(0, size) : found;
		String nextCursor = hasNext ? ActivityCursor.encode(memberId, page.get(page.size() - 1).getId()) : null;
		return new ActivityListResponse(page.stream().map(ActivityResponse::from).toList(), nextCursor, hasNext);
	}
}
