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
@Table(name = "couple_connection_replays", uniqueConstraints = @UniqueConstraint(
		name = "uk_couple_connection_member_key", columnNames = {"member_id", "request_key"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoupleConnectionReplay {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private long memberId;

	@Column(name = "request_key", nullable = false, length = 36)
	private String requestKey;

	@Getter
	@Column(length = 7)
	private String invitationCode;

	@Getter
	private Long coupleId;

	private Instant replayUntil;

	public boolean isReplayable(Instant now) {
		return replayUntil != null && now.isBefore(replayUntil);
	}

	public void complete(String invitationCode, long coupleId, Instant replayUntil) {
		this.invitationCode = invitationCode;
		this.coupleId = coupleId;
		this.replayUntil = replayUntil;
	}
}
