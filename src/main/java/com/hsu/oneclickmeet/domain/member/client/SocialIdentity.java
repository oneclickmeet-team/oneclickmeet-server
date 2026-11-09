package com.hsu.oneclickmeet.domain.member.client;

import com.hsu.oneclickmeet.domain.member.entity.SocialProvider;

/** 소셜 제공자가 확인해 준 사용자 정보. */
public record SocialIdentity(SocialProvider provider, String providerUserId, String nickname, String email) {
}
