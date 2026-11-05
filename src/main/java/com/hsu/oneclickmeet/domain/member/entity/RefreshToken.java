package com.hsu.oneclickmeet.domain.member.entity;

import com.hsu.oneclickmeet.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 갱신 토큰. 원문은 저장하지 않고 해시만 보관하며, 한 번 쓰면 폐기하고 새로 발급한다. */
@Entity
@Getter
@Table(name = "refresh_tokens",
		uniqueConstraints = @UniqueConstraint(name = "uk_refresh_tokens_hash", columnNames = "token_hash"),
		indexes = @Index(name = "idx_refresh_tokens_member", columnList = "member_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private long memberId;

	@Column(nullable = false, updatable = false, length = 64)
	private String tokenHash;

	@Column(nullable = false, updatable = false)
	private Instant expiresAt;

	private Instant revokedAt;

	private RefreshToken(long memberId, String tokenHash, Instant expiresAt) {
		this.memberId = memberId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	public static RefreshToken issue(long memberId, String tokenHash, Instant expiresAt) {
		if (memberId <= 0) {
			throw new IllegalArgumentException("memberId는 양수여야 합니다.");
		}
		if (tokenHash == null || tokenHash.length() != 64) {
			throw new IllegalArgumentException("tokenHash는 SHA-256 16진수 문자열이어야 합니다.");
		}
		if (expiresAt == null) {
			throw new IllegalArgumentException("expiresAt은 필수입니다.");
		}
		return new RefreshToken(memberId, tokenHash, expiresAt);
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}
}
