package com.hsu.oneclickmeet.domain.couple.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CoupleConfig {

	@Bean
	Clock coupleClock() {
		return Clock.systemUTC();
	}
}
