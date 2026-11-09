package com.hsu.oneclickmeet.domain.member.repository;

import com.hsu.oneclickmeet.domain.member.entity.Member;
import com.hsu.oneclickmeet.domain.member.entity.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);
}
