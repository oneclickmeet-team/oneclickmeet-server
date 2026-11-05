package com.hsu.oneclickmeet.domain.member.dto.response;

/** 로그인 결과. newMember가 true면 이번 로그인으로 처음 가입한 것이다. */
public record LoginResponse(TokenResponse tokens, boolean newMember, MemberResponse member) {
}
