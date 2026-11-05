package com.hsu.oneclickmeet.domain.member.client;

import com.hsu.oneclickmeet.domain.member.entity.SocialProvider;

/**
 * 앱이 보낸 소셜 로그인 증표(카카오 액세스 토큰, 구글 ID 토큰)를 제공자에게 확인해 사용자 정보를 얻는다.
 * 증표가 올바르지 않으면 INVALID_SOCIAL_TOKEN, 제공자를 쓸 수 없으면 SOCIAL_LOGIN_UNAVAILABLE로 실패한다.
 * 증표는 로그에 남기지 않는다.
 */
public interface SocialIdentityVerifier {

	SocialProvider provider();

	SocialIdentity verify(String credential);
}
