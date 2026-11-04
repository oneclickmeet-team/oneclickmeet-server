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
public class CourseSaveRequestLock {
	private static final String LOCK_NAME = "SHA2(CONCAT(DATABASE(), ':course-save:', ?), 256)";
	private static final String ACQUIRE = "SELECT GET_LOCK(" + LOCK_NAME + ", 0)";
	private static final String RELEASE = "SELECT RELEASE_LOCK(" + LOCK_NAME + ")";

	private final JdbcTemplate jdbcTemplate;

	public void acquire(long memberId, UUID requestKey) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()
				|| !TransactionSynchronizationManager.isSynchronizationActive()) {
			throw new IllegalStateException("코스 저장 트랜잭션이 필요합니다.");
		}
		String key = memberId + ":" + requestKey;
		jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
			Integer acquired = executeLockQuery(connection, ACQUIRE, key);
			if (Integer.valueOf(0).equals(acquired)) {
				throw new BusinessException(CourseErrorCode.SAVE_IN_PROGRESS);
			}
			if (!Integer.valueOf(1).equals(acquired)) {
				throw new IllegalStateException("코스 저장 요청 잠금을 얻지 못했습니다.");
			}
			Connection transactionConnection = DataSourceUtils.getTargetConnection(connection);
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					// MySQL session locks survive commit and rollback. Release before returning the connection.
					release(transactionConnection, key);
				}
			});
			return null;
		});
	}

	private void release(Connection connection, String key) {
		try {
			if (!Integer.valueOf(1).equals(executeLockQuery(connection, RELEASE, key))) {
				throw new SQLException("코스 저장 요청 잠금이 현재 연결에 없습니다.");
			}
		} catch (SQLException releaseFailure) {
			try {
				connection.abort(Runnable::run);
			} catch (SQLException abortFailure) {
				releaseFailure.addSuppressed(abortFailure);
			}
			log.error("코스 저장 요청 잠금 해제에 실패했습니다.", releaseFailure);
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
