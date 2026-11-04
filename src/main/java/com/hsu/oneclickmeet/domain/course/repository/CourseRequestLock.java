package com.hsu.oneclickmeet.domain.course.repository;

import com.hsu.oneclickmeet.domain.course.exception.CourseErrorCode;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CourseRequestLock {
	private static final String LOCK_NAME = "SHA2(CONCAT(DATABASE(), ?), 256)";
	private static final String ACQUIRE = "SELECT GET_LOCK(" + LOCK_NAME + ", 0)";
	private static final String RELEASE = "SELECT RELEASE_LOCK(" + LOCK_NAME + ")";

	private final JdbcTemplate jdbcTemplate;

	public void acquireSave(long memberId, UUID requestKey) {
		if (!tryAcquire(":course-save:" + memberId + ":" + requestKey)) {
			throw new BusinessException(CourseErrorCode.SAVE_IN_PROGRESS);
		}
	}

	public boolean tryAcquireShare(long memberId, long courseId, UUID requestKey) {
		return tryAcquire(":course-share:" + memberId + ":" + courseId + ":" + requestKey);
	}

	private boolean tryAcquire(String key) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()
				|| !TransactionSynchronizationManager.isSynchronizationActive()) {
			throw new IllegalStateException("코스 요청 트랜잭션이 필요합니다.");
		}
		return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
			Integer acquired = executeLockQuery(connection, ACQUIRE, key);
			if (Integer.valueOf(0).equals(acquired)) {
				return false;
			}
			if (!Integer.valueOf(1).equals(acquired)) {
				throw new IllegalStateException("코스 요청 잠금을 얻지 못했습니다.");
			}
			Connection transactionConnection = DataSourceUtils.getTargetConnection(connection);
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					// MySQL session locks survive commit and rollback. Release before returning the connection.
					release(transactionConnection, key);
				}
			});
			return true;
		}));
	}

	private void release(Connection connection, String key) {
		try {
			if (!Integer.valueOf(1).equals(executeLockQuery(connection, RELEASE, key))) {
				throw new SQLException("코스 요청 잠금이 현재 연결에 없습니다.");
			}
		} catch (SQLException releaseFailure) {
			try {
				connection.abort(Runnable::run);
			} catch (SQLException abortFailure) {
				releaseFailure.addSuppressed(abortFailure);
			}
			log.error("코스 요청 잠금 해제에 실패했습니다.", releaseFailure);
		}
	}

	private Integer executeLockQuery(Connection connection, String sql, String key) throws SQLException {
		try (var statement = connection.prepareStatement(sql)) {
			statement.setString(1, key);
			try (var result = statement.executeQuery()) {
				result.next();
				return result.getObject(1, Integer.class);
			}
		}
	}
}
