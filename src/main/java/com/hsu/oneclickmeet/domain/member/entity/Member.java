package com.hsu.oneclickmeet.domain.member.entity;

import com.hsu.oneclickmeet.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원. 소셜 로그인 제공자와 그쪽 사용자 번호가 같으면 같은 회원이다. */
@Entity
@Getter
@Table(name = "members", uniqueConstraints = @UniqueConstraint(name = Member.PROVIDER_USER_UNIQUE,
		columnNames = {"provider", "provider_user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

	public static final String PROVIDER_USER_UNIQUE = "uk_members_provider_user";
	public static final int MAX_NICKNAME_LENGTH = 30;
	static final int MAX_EMAIL_LENGTH = 254;
	private static final String DEFAULT_NICKNAME = "사용자";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// provider와 providerUserId는 가입 코드(signUp)가 항상 채운다. DB에서는 비워 둘 수 있게 했는데,
	// 다른 도메인의 통합 테스트가 members(id, name)만 직접 넣기 때문이다. 그 테스트가 정리되면 NOT NULL로 바꾼다.
	@Enumerated(EnumType.STRING)
	@Column(updatable = false, length = 20)
	private SocialProvider provider;

	@Column(updatable = false, length = 100)
	private String providerUserId;

	/** 다른 도메인이 members.name을 표시 이름으로 직접 조회하므로 커럼 이름은 name이다. */
	@Column(name = "name", nullable = false, length = 100)
	private String nickname;

	@Column(length = MAX_EMAIL_LENGTH)
	private String email;

	private Member(SocialProvider provider, String providerUserId, String nickname, String email) {
		this.provider = provider;
		this.providerUserId = providerUserId;
		this.nickname = nickname;
		this.email = email;
	}

	/** 소셜 로그인으로 처음 들어온 회원을 만든다. 닉네임이 없거나 길면 기본값·잘라낸 값을 쓴다. */
	public static Member signUp(SocialProvider provider, String providerUserId, String nickname, String email) {
		if (provider == null) {
			throw new IllegalArgumentException("provider는 필수입니다.");
		}
		if (providerUserId == null || providerUserId.isBlank() || providerUserId.trim().length() > 100) {
			throw new IllegalArgumentException("providerUserId는 1~100자여야 합니다.");
		}
		String cleanNickname = nickname == null || nickname.isBlank() ? DEFAULT_NICKNAME : nickname.trim();
		if (cleanNickname.length() > MAX_NICKNAME_LENGTH) {
			cleanNickname = cleanNickname.substring(0, MAX_NICKNAME_LENGTH);
		}
		String cleanEmail = email == null || email.isBlank() || email.trim().length() > MAX_EMAIL_LENGTH
				? null : email.trim();
		return new Member(provider, providerUserId.trim(), cleanNickname, cleanEmail);
	}

	public void changeNickname(String nickname) {
		if (nickname == null || nickname.isBlank() || nickname.trim().length() > MAX_NICKNAME_LENGTH) {
			throw new IllegalArgumentException("nickname은 1~" + MAX_NICKNAME_LENGTH + "자여야 합니다.");
		}
		this.nickname = nickname.trim();
	}
}
