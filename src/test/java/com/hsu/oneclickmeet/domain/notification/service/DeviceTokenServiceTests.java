package com.hsu.oneclickmeet.domain.notification.service;

import com.hsu.oneclickmeet.domain.notification.entity.DevicePlatform;
import com.hsu.oneclickmeet.domain.notification.repository.DeviceTokenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DeviceTokenServiceTests {

	private static final long MEMBER = 860_001L;
	private static final long OTHER = 860_002L;
	private static final String PREFIX = "devtest-";

	@Autowired
	private DeviceTokenService service;
	@Autowired
	private DeviceTokenRepository repository;
	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from device_tokens where member_id in (?, ?)", MEMBER, OTHER);
	}

	private int count(long memberId) {
		return jdbc.queryForObject("select count(*) from device_tokens where member_id = ?", Integer.class, memberId);
	}

	@Test
	@DisplayName("토큰을 등록하고, 같은 토큰을 다시 등록해도 한 건만 남는다")
	void registersIdempotently() {
		service.register(MEMBER, DevicePlatform.ANDROID, PREFIX + "a");
		service.register(MEMBER, DevicePlatform.ANDROID, " " + PREFIX + "a ");

		assertThat(count(MEMBER)).isEqualTo(1);
		assertThat(repository.findByToken(PREFIX + "a")).get().satisfies(token -> {
			assertThat(token.getMemberId()).isEqualTo(MEMBER);
			assertThat(token.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
		});
	}

	@Test
	@DisplayName("다른 회원이 같은 기기 토큰을 등록하면 새 회원에게 넘어간다")
	void movesTokenToNewMember() {
		service.register(MEMBER, DevicePlatform.IOS, PREFIX + "shared");
		service.register(OTHER, DevicePlatform.ANDROID, PREFIX + "shared");

		assertThat(count(MEMBER)).isZero();
		assertThat(count(OTHER)).isEqualTo(1);
		assertThat(repository.findByToken(PREFIX + "shared")).get().satisfies(token ->
			assertThat(token.getPlatform()).isEqualTo(DevicePlatform.ANDROID));
	}

	@Test
	@DisplayName("같은 토큰을 동시에 등록해도 한 건만 만들어진다")
	void concurrentRegistrationCreatesOneRow() throws Exception {
		int threads = 6;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			futures.add(executor.submit(() -> {
				start.await();
				service.register(MEMBER, DevicePlatform.ANDROID, PREFIX + "race");
				return null;
			}));
		}
		start.countDown();
		for (Future<?> future : futures) {
			future.get();
		}
		executor.shutdown();

		assertThat(jdbc.queryForObject("select count(*) from device_tokens where token = ?", Integer.class, PREFIX + "race"))
			.isEqualTo(1);
	}

	@Test
	@DisplayName("한 회원의 토큰은 10개까지이고 넘으면 가장 오래된 것부터 지운다")
	void keepsOnlyNewestTenTokens() {
		for (int i = 1; i <= 12; i++) {
			service.register(MEMBER, DevicePlatform.ANDROID, PREFIX + "t" + i);
			jdbc.update("update device_tokens set updated_at = date_add('2026-01-01', interval ? second) where token = ?",
				i, PREFIX + "t" + i);
		}
		service.register(MEMBER, DevicePlatform.ANDROID, PREFIX + "t13");

		assertThat(count(MEMBER)).isEqualTo(10);
		assertThat(repository.findByToken(PREFIX + "t13")).isPresent();
		assertThat(repository.findByToken(PREFIX + "t1")).isEmpty();
	}

	@Test
	@DisplayName("해제는 본인의 토큰만 지우고, 없거나 남의 토큰이어도 성공으로 본다")
	void unregistersOnlyOwnToken() {
		service.register(MEMBER, DevicePlatform.ANDROID, PREFIX + "mine");
		service.register(OTHER, DevicePlatform.IOS, PREFIX + "theirs");

		service.unregister(MEMBER, PREFIX + "theirs");
		service.unregister(MEMBER, PREFIX + "unknown");
		assertThat(count(OTHER)).isEqualTo(1);

		service.unregister(MEMBER, " " + PREFIX + "mine ");
		service.unregister(MEMBER, PREFIX + "mine");
		assertThat(count(MEMBER)).isZero();
	}
}
