package com.hsu.oneclickmeet.global.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** 접근 토큰(JWT, HS256) 발급과 검증. 토큰의 subject가 회원 번호다. */
@Slf4j
@Component
public class JwtTokenProvider {

	private static final int MIN_SECRET_BYTES = 32;
	private static final Duration CLOCK_SKEW = Duration.ofSeconds(10);
	private static final String TOKEN_TYPE_CLAIM = "typ";
	private static final String ACCESS_TYPE = "access";

	private final JwtProperties properties;
	private final Clock clock;
	private final JwtEncoder encoder;
	private final JwtDecoder decoder;

	public JwtTokenProvider(JwtProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		byte[] secret = secretBytes(properties.secret());
		SecretKeySpec key = new SecretKeySpec(secret, "HmacSHA256");
		this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
		NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
		timestamps.setClock(clock);
		nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(properties.issuer())));
		this.decoder = nimbus;
	}

	private static byte[] secretBytes(String configured) {
		if (configured == null || configured.isBlank()) {
			log.warn("JWT_SECRET이 설정되지 않아 임시 키를 사용합니다. 재시작하면 발급한 토큰이 모두 무효가 됩니다.");
			byte[] random = new byte[MIN_SECRET_BYTES];
			new SecureRandom().nextBytes(random);
			return random;
		}
		byte[] bytes = configured.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("JWT_SECRET은 32바이트 이상이어야 합니다.");
		}
		return bytes;
	}

	public record IssuedToken(String value, long expiresInSeconds) {
	}

	public IssuedToken issueAccessToken(long memberId) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(properties.accessTokenTtl());
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.issuer())
				.subject(Long.toString(memberId))
				.issuedAt(now)
				.expiresAt(expiresAt)
				.claim(TOKEN_TYPE_CLAIM, ACCESS_TYPE)
				.build();
		String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
		return new IssuedToken(token, properties.accessTokenTtl().toSeconds());
	}

	/** 유효한 접근 토큰이면 회원 번호를, 서명·만료·발급자·종류가 맞지 않으면 빈 값을 돌려준다. */
	public Optional<Long> parseAccessToken(String token) {
		try {
			Jwt jwt = decoder.decode(token);
			if (!ACCESS_TYPE.equals(jwt.getClaimAsString(TOKEN_TYPE_CLAIM))) {
				return Optional.empty();
			}
			long memberId = Long.parseLong(jwt.getSubject());
			return memberId > 0 ? Optional.of(memberId) : Optional.empty();
		} catch (JwtException | NumberFormatException exception) {
			return Optional.empty();
		}
	}
}
