package com.hsu.oneclickmeet.domain.member.service;

import com.hsu.oneclickmeet.domain.member.client.SocialIdentity;
import com.hsu.oneclickmeet.domain.member.client.SocialIdentityVerifier;
import com.hsu.oneclickmeet.domain.member.dto.response.LoginResponse;
import com.hsu.oneclickmeet.domain.member.dto.response.TokenResponse;
import com.hsu.oneclickmeet.domain.member.entity.SocialProvider;
import com.hsu.oneclickmeet.domain.member.exception.MemberErrorCode;
import com.hsu.oneclickmeet.domain.member.repository.MemberRepository;
import com.hsu.oneclickmeet.domain.member.repository.RefreshTokenRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.security.JwtProperties;
import com.hsu.oneclickmeet.global.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceTests {

	private static final String PREFIX = "authtest-";

	@Autowired
	private MemberRepository memberRepository;
	@Autowired
	private RefreshTokenRepository refreshTokenRepository;
	@Autowired
	private JwtTokenProvider tokenProvider;
	@Autowired
	private JwtProperties jwtProperties;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private JdbcTemplate jdbc;

	private final MutableClock clock = new MutableClock(Instant.now());
	private final FakeVerifier kakao = new FakeVerifier(SocialProvider.KAKAO);

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from refresh_tokens where member_id in (select id from members where provider_user_id like ?)",
			PREFIX + "%");
		jdbc.update("delete from members where provider_user_id like ?", PREFIX + "%");
	}

	private AuthService service() {
		return new AuthService(memberRepository, refreshTokenRepository, List.of(kakao), tokenProvider, jwtProperties,
			clock, transactionManager);
	}

	private static void assertCode(Throwable thrown, MemberErrorCode code) {
		assertThat(thrown).isInstanceOf(BusinessException.class);
		assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(code);
	}

	private LoginResponse login(AuthService service, String userId) {
		kakao.identity = new SocialIdentity(SocialProvider.KAKAO, PREFIX + userId, "닉네임", userId + "@test.com");
		return service.login(SocialProvider.KAKAO, "kakao-access-token");
	}

	@Test
	@DisplayName("처음 로그인하면 회원을 만들고 접근 토큰과 갱신 토큰을 발급한다")
	void signsUpNewMember() {
		LoginResponse response = login(service(), "a");

		assertThat(response.newMember()).isTrue();
		assertThat(response.member().nickname()).isEqualTo("닉네임");
		assertThat(response.member().provider()).isEqualTo(SocialProvider.KAKAO);
		assertThat(response.tokens().tokenType()).isEqualTo("Bearer");
		assertThat(response.tokens().refreshToken()).isNotBlank();
		assertThat(tokenProvider.parseAccessToken(response.tokens().accessToken())).contains(response.member().memberId());
		assertThat(jdbc.queryForObject("select count(*) from members where provider_user_id = ?", Integer.class, PREFIX + "a"))
			.isEqualTo(1);
	}

	@Test
	@DisplayName("다시 로그인하면 같은 회원으로 로그인하고 가입은 한 번만 일어난다")
	void logsInExistingMember() {
		AuthService service = service();
		LoginResponse first = login(service, "a");
		LoginResponse second = login(service, "a");

		assertThat(second.newMember()).isFalse();
		assertThat(second.member().memberId()).isEqualTo(first.member().memberId());
		assertThat(second.tokens().refreshToken()).isNotEqualTo(first.tokens().refreshToken());
	}

	@Test
	@DisplayName("같은 사용자가 동시에 처음 로그인해도 회원은 한 명만 만들어진다")
	void concurrentFirstLoginCreatesOneMember() throws Exception {
		AuthService service = service();
		kakao.identity = new SocialIdentity(SocialProvider.KAKAO, PREFIX + "race", "닉네임", null);
		int threads = 6;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<LoginResponse>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			futures.add(executor.submit(() -> {
				start.await();
				return service.login(SocialProvider.KAKAO, "t");
			}));
		}
		start.countDown();
		List<LoginResponse> responses = new ArrayList<>();
		for (Future<LoginResponse> future : futures) {
			responses.add(future.get());
		}
		executor.shutdown();

		assertThat(responses).extracting(r -> r.member().memberId()).containsOnly(responses.get(0).member().memberId());
		assertThat(responses).filteredOn(LoginResponse::newMember).hasSize(1);
		assertThat(jdbc.queryForObject("select count(*) from members where provider_user_id = ?", Integer.class, PREFIX + "race"))
			.isEqualTo(1);
	}

	@Test
	@DisplayName("소셜 확인이 실패하면 회원도 토큰도 만들지 않는다")
	void failedVerificationCreatesNothing() {
		kakao.failure = new BusinessException(MemberErrorCode.INVALID_SOCIAL_TOKEN);

		assertThatThrownBy(() -> service().login(SocialProvider.KAKAO, "bad"))
			.satisfies(thrown -> assertCode(thrown, MemberErrorCode.INVALID_SOCIAL_TOKEN));
		assertThat(jdbc.queryForObject("select count(*) from members where provider_user_id like ?", Integer.class, PREFIX + "%"))
			.isZero();
	}

	@Test
	@DisplayName("지원하지 않는 제공자는 사용할 수 없음으로 처리한다")
	void unsupportedProvider() {
		assertThatThrownBy(() -> service().login(SocialProvider.GOOGLE, "t"))
			.satisfies(thrown -> assertCode(thrown, MemberErrorCode.SOCIAL_LOGIN_UNAVAILABLE));
	}

	@Test
	@DisplayName("갱신하면 새 토큰 한 쌍을 주고 이전 갱신 토큰은 더 이상 쓸 수 없다")
	void refreshRotatesTokens() {
		AuthService service = service();
		LoginResponse login = login(service, "a");

		TokenResponse refreshed = service.refresh(login.tokens().refreshToken());

		assertThat(refreshed.refreshToken()).isNotEqualTo(login.tokens().refreshToken());
		assertThat(tokenProvider.parseAccessToken(refreshed.accessToken())).contains(login.member().memberId());
		assertThatThrownBy(() -> service.refresh(login.tokens().refreshToken()))
			.satisfies(thrown -> assertCode(thrown, MemberErrorCode.INVALID_REFRESH_TOKEN));
	}

	@Test
	@DisplayName("이미 쓴 갱신 토큰이 다시 오면 그 회원의 모든 갱신 토큰을 폐기한다")
	void reuseRevokesAllTokensOfMember() {
		AuthService service = service();
		LoginResponse login = login(service, "a");
		TokenResponse rotated = service.refresh(login.tokens().refreshToken());

		assertThatThrownBy(() -> service.refresh(login.tokens().refreshToken()))
			.satisfies(thrown -> assertCode(thrown, MemberErrorCode.INVALID_REFRESH_TOKEN));

		assertThatThrownBy(() -> service.refresh(rotated.refreshToken()))
			.satisfies(thrown -> assertCode(thrown, MemberErrorCode.INVALID_REFRESH_TOKEN));
	}

	@Test
	@DisplayName("같은 갱신 토큰을 동시에 쓰면 한 번만 성공한다")
	void concurrentRefreshSucceedsOnce() throws Exception {
		AuthService service = service();
		LoginResponse login = login(service, "a");
		int threads = 4;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			futures.add(executor.submit(() -> {
				start.await();
				try {
					service.refresh(login.tokens().refreshToken());
					return true;
				} catch (BusinessException exception) {
					return false;
				}
			}));
		}
		start.countDown();
		int successes = 0;
		for (Future<Boolean> future : futures) {
			if (future.get()) {
				successes++;
			}
		}
		executor.shutdown();

		assertThat(successes).isEqualTo(1);
	}

	@Test
	@DisplayName("만료된 갱신 토큰과 모르는 토큰은 거절한다")
	void rejectsExpiredAndUnknownRefreshTokens() {
		AuthService service = service();
		LoginResponse login = login(service, "a");

		assertThatThrownBy(() -> service.refresh("unknown-token"))
			.satisfies(thrown -> assertCode(thrown, MemberErrorCode.INVALID_REFRESH_TOKEN));

		clock.set(clock.instant().plus(jwtProperties.refreshTokenTtl()).plus(Duration.ofMinutes(1)));
		assertThatThrownBy(() -> service.refresh(login.tokens().refreshToken()))
			.satisfies(thrown -> assertCode(thrown, MemberErrorCode.INVALID_REFRESH_TOKEN));
	}

	@Test
	@DisplayName("로그아웃하면 그 갱신 토큰을 폐기하고, 다시 해도 안전하며, 다른 회원의 토큰은 건드리지 않는다")
	void logoutRevokesOwnTokenOnly() {
		AuthService service = service();
		LoginResponse mine = login(service, "a");
		LoginResponse others = login(service, "b");

		service.logout(others.member().memberId(), mine.tokens().refreshToken());
		assertThat(service.refresh(mine.tokens().refreshToken())).isNotNull();

		LoginResponse again = login(service, "a");
		service.logout(again.member().memberId(), again.tokens().refreshToken());
		service.logout(again.member().memberId(), again.tokens().refreshToken());
		service.logout(again.member().memberId(), "unknown");

		assertThatThrownBy(() -> service.refresh(again.tokens().refreshToken()))
			.satisfies(thrown -> assertCode(thrown, MemberErrorCode.INVALID_REFRESH_TOKEN));
		assertThat(service.refresh(others.tokens().refreshToken())).isNotNull();
	}

	@Test
	@DisplayName("갱신 토큰 원문은 저장하지 않고 해시만 저장한다")
	void storesOnlyHash() {
		LoginResponse login = login(service(), "a");

		Integer raw = jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class,
			login.tokens().refreshToken());

		assertThat(raw).isZero();
		assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where member_id = ?", Integer.class,
			login.member().memberId())).isEqualTo(1);
	}

	private static class FakeVerifier implements SocialIdentityVerifier {

		private final SocialProvider provider;
		private SocialIdentity identity;
		private RuntimeException failure;

		FakeVerifier(SocialProvider provider) {
			this.provider = provider;
		}

		@Override
		public SocialProvider provider() {
			return provider;
		}

		@Override
		public SocialIdentity verify(String credential) {
			if (failure != null) {
				throw failure;
			}
			return identity;
		}
	}

	private static class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void set(Instant instant) {
			this.now = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}
