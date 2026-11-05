package com.hsu.oneclickmeet.domain.notification.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 푸시 연동(FCM)이 준비되기 전까지 아무것도 보내지 않는 기본 구현. 실제 구현 빈이 있으면 그것을 쓴다. */
@Configuration(proxyBeanMethods = false)
class PushSenderConfig {

	@Bean
	@ConditionalOnMissingBean(PushSender.class)
	PushSender noopPushSender() {
		return (memberId, title, body) -> {
		};
	}
}
