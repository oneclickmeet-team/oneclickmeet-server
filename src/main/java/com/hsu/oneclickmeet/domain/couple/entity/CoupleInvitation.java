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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@Table(name = "couple_invitations",
	indexes = @Index(name = "idx_couple_invitations_issuer_purpose", columnList = "issuer_id, purpose"),
	uniqueConstraints = @UniqueConstraint(name = "uk_couple_invitations_code", columnNames = "code"),
	check = @CheckConstraint(name = "chk_couple_invitations_purpose",
		constraint = "(purpose = 'CONNECT' and couple_id is null) or (purpose = 'RECONNECT' and couple_id is not null)"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoupleInvitation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "issuer_id", nullable = false)
	private long issuerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private InvitationPurpose purpose;

	@Column(nullable = false, length = 7)
	private String code;

	@Column(name = "couple_id")
	private Long coupleId;

	@Column(nullable = false)
	private Instant expiresAt;

	@Getter(AccessLevel.NONE)
	@Column(nullable = false)
	private boolean consumed;

	@Getter(AccessLevel.NONE)
	@Column(nullable = false)
	private boolean revoked;

	private CoupleInvitation(long issuerId, InvitationPurpose purpose, String code, Long coupleId, Instant expiresAt) {
		this.issuerId = issuerId;
		this.purpose = purpose;
		this.code = code;
		this.coupleId = coupleId;
		this.expiresAt = expiresAt;
	}

	public static CoupleInvitation issue(long issuerId, InvitationPurpose purpose, String code, Long coupleId,
			Instant expiresAt) {
		return new CoupleInvitation(issuerId, purpose, code, coupleId, expiresAt);
	}

	public boolean isAvailable(Instant now) {
		return !isUsedOrRevoked() && now.isBefore(expiresAt);
	}

	public boolean isUsedOrRevoked() {
		return consumed || revoked;
	}

	public void consume() {
		consumed = true;
	}

	public void revoke() {
		revoked = true;
	}
}
