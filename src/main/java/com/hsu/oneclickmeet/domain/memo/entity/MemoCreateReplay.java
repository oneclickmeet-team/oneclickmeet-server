package com.hsu.oneclickmeet.domain.memo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "memo_create_replays", uniqueConstraints = @UniqueConstraint(
		name = "uk_memo_create_replays_author_key", columnNames = {"author_id", "request_key"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemoCreateReplay {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private long authorId;

	@Column(nullable = false, updatable = false, length = 16)
	private UUID requestKey;

	@Column(nullable = false, length = 64)
	private String requestHash;

	@Column(nullable = false)
	private long memoId;

	@Column(nullable = false)
	private Instant memoCreatedAt;

	@Column(nullable = false)
	private Instant memoUpdatedAt;

	@Column(nullable = false)
	private Instant expiresAt;

	public MemoCreateReplay(long authorId, UUID requestKey) {
		this.authorId = authorId;
		this.requestKey = requestKey;
	}

	public boolean isReplayable(Instant now) {
		return now.isBefore(expiresAt);
	}

	public void complete(String requestHash, Memo memo, Instant expiresAt) {
		this.requestHash = requestHash;
		this.memoId = memo.getId();
		this.memoCreatedAt = memo.getCreatedAt();
		this.memoUpdatedAt = memo.getUpdatedAt();
		this.expiresAt = expiresAt;
	}
}
