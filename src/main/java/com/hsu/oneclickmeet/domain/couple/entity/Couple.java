package com.hsu.oneclickmeet.domain.couple.entity;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@Table(name = "couples",
	indexes = {
		@Index(name = "idx_couples_first_member", columnList = "first_member_id"),
		@Index(name = "idx_couples_second_member", columnList = "second_member_id")
	},
	check = @CheckConstraint(name = "chk_couples_distinct_members", constraint = "first_member_id <> second_member_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Couple {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "first_member_id", nullable = false)
	private long firstMemberId;

	@Column(name = "second_member_id", nullable = false)
	private long secondMemberId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CoupleStatus status;

	@Column(nullable = false)
	private Instant connectedAt;

	private Instant disconnectedAt;
	private Instant graceEndsAt;

	private Couple(long firstMemberId, long secondMemberId, Instant now) {
		this.firstMemberId = firstMemberId;
		this.secondMemberId = secondMemberId;
		this.status = CoupleStatus.CONNECTED;
		this.connectedAt = now;
	}

	public static Couple connect(long firstMemberId, long secondMemberId, Instant now) {
		if (firstMemberId == secondMemberId) {
			throw new IllegalArgumentException("커플은 서로 다른 두 회원으로 구성해야 합니다.");
		}
		return new Couple(firstMemberId, secondMemberId, now);
	}

	public long partnerId(long memberId) {
		if (memberId == firstMemberId) {
			return secondMemberId;
		}
		if (memberId == secondMemberId) {
			return firstMemberId;
		}
		throw new IllegalArgumentException("관계의 구성원이 아닙니다.");
	}
}
