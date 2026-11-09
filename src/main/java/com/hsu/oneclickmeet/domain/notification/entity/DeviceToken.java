package com.hsu.oneclickmeet.domain.notification.entity;

import com.hsu.oneclickmeet.global.entity.BaseEntity;
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

/**
 * 푸시 알림을 받을 기기 토큰. 토큰 하나는 한 회원에게만 속한다.
 * 같은 기기에서 다른 회원이 로그인해 같은 토큰을 등록하면 새 회원에게 넘어간다.
 * 토큰은 사용자 기기를 식별하는 값이므로 로그에 남기지 않는다.
 */
@Entity
@Getter
@Table(name = "device_tokens",
		uniqueConstraints = @UniqueConstraint(name = "uk_device_tokens_token", columnNames = "token"),
		indexes = @Index(name = "idx_device_tokens_member", columnList = "member_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken extends BaseEntity {

	public static final int MAX_TOKEN_LENGTH = 512;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private long memberId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private DevicePlatform platform;

	@Column(nullable = false, updatable = false, length = MAX_TOKEN_LENGTH)
	private String token;

	private DeviceToken(long memberId, DevicePlatform platform, String token) {
		this.memberId = memberId;
		this.platform = platform;
		this.token = token;
	}

	public static DeviceToken register(long memberId, DevicePlatform platform, String token) {
		validate(memberId, platform, token);
		return new DeviceToken(memberId, platform, token.trim());
	}

	/** 이미 등록된 토큰을 지금 요청한 회원·플랫폼으로 옮긴다. */
	public void reassign(long memberId, DevicePlatform platform) {
		validate(memberId, platform, token);
		this.memberId = memberId;
		this.platform = platform;
	}

	private static void validate(long memberId, DevicePlatform platform, String token) {
		if (memberId <= 0) {
			throw new IllegalArgumentException("memberId는 양수여야 합니다.");
		}
		if (platform == null) {
			throw new IllegalArgumentException("platform은 필수입니다.");
		}
		if (token == null || token.isBlank() || token.trim().length() > MAX_TOKEN_LENGTH) {
			throw new IllegalArgumentException("token은 1~" + MAX_TOKEN_LENGTH + "자여야 합니다.");
		}
	}
}
