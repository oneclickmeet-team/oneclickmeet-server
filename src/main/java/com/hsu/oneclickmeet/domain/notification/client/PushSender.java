package com.hsu.oneclickmeet.domain.notification.client;

/** 알림을 기기로 푸시하는 외부 연동(FCM 등). 실패해도 알림 저장에는 영향을 주지 않는다. */
public interface PushSender {

	void send(long memberId, String title, String body);
}
