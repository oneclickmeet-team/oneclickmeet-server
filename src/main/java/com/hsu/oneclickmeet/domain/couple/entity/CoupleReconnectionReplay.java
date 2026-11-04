package com.hsu.oneclickmeet.domain.couple.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "couple_reconnection_replays", uniqueConstraints = @UniqueConstraint(
	name = "uk_couple_reconnection_member_couple_key", columnNames = {"member_id", "couple_id", "request_key"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoupleReconnectionReplay {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private long memberId;

	@Column(name = "couple_id", nullable = false)
	private long coupleId;

	@Column(name = "request_key", nullable = false, length = 36)
	private String requestKey;

	@Getter
	@Column(nullable = false, length = 7)
	private String invitationCode;

	@Column(nullable = false)
	private Instant replayUntil;

	public CoupleReconnectionReplay(long memberId, long coupleId, String requestKey) {
		this.memberId = memberId;
		this.coupleId = coupleId;
		this.requestKey = requestKey;
	}

	public boolean isReplayable(Instant now) {
		return replayUntil != null && now.isBefore(replayUntil);
	}

	public void complete(String invitationCode, Instant replayUntil) {
		this.invitationCode = invitationCode;
		this.replayUntil = replayUntil;
	}
}
