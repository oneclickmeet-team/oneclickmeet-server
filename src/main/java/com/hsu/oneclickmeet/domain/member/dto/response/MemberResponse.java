package com.hsu.oneclickmeet.domain.member.dto.response;

import com.hsu.oneclickmeet.domain.member.entity.Member;
import com.hsu.oneclickmeet.domain.member.entity.SocialProvider;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record MemberResponse(long memberId, SocialProvider provider, String nickname, String email, Instant createdAt) {

	public static MemberResponse from(Member member) {
		return new MemberResponse(member.getId(), member.getProvider(), member.getNickname(), member.getEmail(),
				member.getCreatedAt() == null ? null : member.getCreatedAt().truncatedTo(ChronoUnit.MICROS));
	}
}
