package com.hsu.oneclickmeet.domain.member.service;

import com.hsu.oneclickmeet.domain.member.client.SocialIdentity;
import com.hsu.oneclickmeet.domain.member.client.SocialIdentityVerifier;
import com.hsu.oneclickmeet.domain.member.dto.response.LoginResponse;
import com.hsu.oneclickmeet.domain.member.dto.response.MemberResponse;
import com.hsu.oneclickmeet.domain.member.dto.response.TokenResponse;
import com.hsu.oneclickmeet.domain.member.entity.Member;
import com.hsu.oneclickmeet.domain.member.entity.RefreshToken;
import com.hsu.oneclickmeet.domain.member.entity.SocialProvider;
import com.hsu.oneclickmeet.domain.member.exception.MemberErrorCode;
import com.hsu.oneclickmeet.domain.member.repository.MemberRepository;
import com.hsu.oneclickmeet.domain.member.repository.RefreshTokenRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.security.JwtProperties;
import com.hsu.oneclickmeet.global.security.JwtTokenProvider;
import com.hsu.oneclickmeet.global.security.JwtTokenProvider.IssuedToken;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 소셜 로그인, 토큰 갱신, 로그아웃.
 * 갱신 토큰은 한 번 쓰면 폐기하고 새로 발급한다. 이미 폐기된 토큰이 다시 쓰이면 탈취를 의심해
 * 그 회원의 모든 갱신 토큰을 폐기한다. 토큰 원문은 저장·로그하지 않는다.
 */
@Service
public class AuthService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final MemberRepository memberRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final Map<SocialProvider, SocialIdentityVerifier> verifiers = new EnumMap<>(SocialProvider.class);
	private final JwtTokenProvider tokenProvider;
	private final JwtProperties jwtProperties;
	private final Clock clock;
	private final TransactionTemplate transaction;

	public AuthService(MemberRepository memberRepository, RefreshTokenRepository refreshTokenRepository,
			List<SocialIdentityVerifier> verifiers, JwtTokenProvider tokenProvider, JwtProperties jwtProperties,
			Clock clock, PlatformTransactionManager transactionManager) {
		this.memberRepository = memberRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		verifiers.forEach(verifier -> this.verifiers.put(verifier.provider(), verifier));
		this.tokenProvider = tokenProvider;
		this.jwtProperties = jwtProperties;
		this.clock = clock;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/** 소셜 증표를 제공자에게 확인한 뒤 회원을 찾거나 만들고 토큰을 발급한다. */
	public LoginResponse login(SocialProvider provider, String credential) {
		SocialIdentityVerifier verifier = verifiers.get(provider);
		if (verifier == null) {
			throw new BusinessException(MemberErrorCode.SOCIAL_LOGIN_UNAVAILABLE);
		}
		SocialIdentity identity = verifier.verify(credential.trim());
		try {
			return transaction.execute(status -> signIn(identity));
		} catch (DataIntegrityViolationException exception) {
			// 같은 사용자가 동시에 처음 로그인해 다른 요청이 먼저 가입을 마친 경우: 먼저 생긴 회원으로 로그인한다.
			return transaction.execute(status -> signIn(identity));
		}
	}

	/** 갱신 토큰으로 새 토큰 한 쌍을 발급한다. 쓸 수 없는 토큰이면 모두 같은 401이다. */
	public TokenResponse refresh(String rawRefreshToken) {
		String hash = hash(rawRefreshToken.trim());
		TokenResponse issued = transaction.execute(status -> {
			RefreshToken token = refreshTokenRepository.findByTokenHash(hash).orElse(null);
			if (token == null) {
				return null;
			}
			Instant now = clock.instant();
			if (token.isRevoked() || refreshTokenRepository.revoke(token.getId(), now) == 0) {
				// 이미 쓴 토큰이 다시 왔다: 탈취 가능성이 있으므로 이 회원의 로그인을 모두 끊는다.
				refreshTokenRepository.revokeAllOf(token.getMemberId(), now);
				return null;
			}
			if (token.isExpired(now) || !memberRepository.existsById(token.getMemberId())) {
				return null;
			}
			return issueTokens(token.getMemberId());
		});
		if (issued == null) {
			throw new BusinessException(MemberErrorCode.INVALID_REFRESH_TOKEN);
		}
		return issued;
	}

	/** 본인의 갱신 토큰을 폐기한다. 이미 폐기됐거나 없는 토큰이어도 성공으로 본다. */
	public void logout(long memberId, String rawRefreshToken) {
		String hash = hash(rawRefreshToken.trim());
		transaction.executeWithoutResult(status -> refreshTokenRepository.findByTokenHash(hash)
				.filter(token -> token.getMemberId() == memberId)
				.ifPresent(token -> refreshTokenRepository.revoke(token.getId(), clock.instant())));
	}

	private LoginResponse signIn(SocialIdentity identity) {
		boolean[] created = {false};
		Member member = memberRepository.findByProviderAndProviderUserId(identity.provider(), identity.providerUserId())
				.orElseGet(() -> {
					created[0] = true;
					return memberRepository.saveAndFlush(Member.signUp(identity.provider(), identity.providerUserId(),
							identity.nickname(), identity.email()));
				});
		return new LoginResponse(issueTokens(member.getId()), created[0], MemberResponse.from(member));
	}

	private TokenResponse issueTokens(long memberId) {
		IssuedToken access = tokenProvider.issueAccessToken(memberId);
		byte[] random = new byte[32];
		RANDOM.nextBytes(random);
		String rawRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
		refreshTokenRepository.save(RefreshToken.issue(memberId, hash(rawRefresh),
				clock.instant().plus(jwtProperties.refreshTokenTtl())));
		return TokenResponse.bearer(access.value(), rawRefresh, access.expiresInSeconds());
	}

	private static String hash(String rawToken) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
