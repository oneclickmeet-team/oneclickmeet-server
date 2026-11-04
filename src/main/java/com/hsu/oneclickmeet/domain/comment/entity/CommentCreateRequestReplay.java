package com.hsu.oneclickmeet.domain.comment.entity;

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
@Table(name = "comment_create_requests", uniqueConstraints = @UniqueConstraint(
		name = "uk_comment_create_requests_member_key", columnNames = {"member_id", "request_key"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentCreateRequestReplay {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private long memberId;

	@Column(nullable = false, updatable = false, length = 16)
	private UUID requestKey;

	@Column(nullable = false, columnDefinition = "text")
	private String responseJson;

	@Column(nullable = false)
	private Instant expiresAt;

	public CommentCreateRequestReplay(long memberId, UUID requestKey) {
		this.memberId = memberId;
		this.requestKey = requestKey;
	}

	public void complete(String responseJson, Instant expiresAt) {
		this.responseJson = responseJson;
		this.expiresAt = expiresAt;
	}

	public boolean isReplayable(Instant now) {
		return now.isBefore(expiresAt);
	}
}
