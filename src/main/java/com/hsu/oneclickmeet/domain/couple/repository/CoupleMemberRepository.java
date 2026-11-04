package com.hsu.oneclickmeet.domain.couple.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Reads the member columns in the #19 contract draft until the member API is available. */
@Repository
@RequiredArgsConstructor
public class CoupleMemberRepository {

	private final JdbcTemplate jdbcTemplate;

	public Optional<String> findDisplayName(long memberId) {
		return jdbcTemplate.queryForList("select name from members where id = ?", String.class, memberId)
			.stream().findFirst();
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public boolean lock(long memberId) {
		return !jdbcTemplate.queryForList("select id from members where id = ? for update", Long.class, memberId)
			.isEmpty();
	}
}
