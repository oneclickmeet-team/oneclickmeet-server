package com.hsu.oneclickmeet.domain.member.service;

import com.hsu.oneclickmeet.domain.member.dto.response.MemberResponse;
import com.hsu.oneclickmeet.domain.member.entity.Member;
import com.hsu.oneclickmeet.domain.member.entity.SocialProvider;
import com.hsu.oneclickmeet.domain.member.exception.MemberErrorCode;
import com.hsu.oneclickmeet.domain.member.repository.MemberRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class MemberServiceTests {

	private static final String PREFIX = "membertest-";

	@Autowired
	private MemberService service;
	@Autowired
	private MemberRepository repository;
	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from members where provider_user_id like ?", PREFIX + "%");
	}

	private long member(String userId, String nickname) {
		return repository.saveAndFlush(Member.signUp(SocialProvider.GOOGLE, PREFIX + userId, nickname, "m@test.com")).getId();
	}

	@Test
	@DisplayName("내 정보를 조회한다")
	void readsOwnProfile() {
		long id = member("a", "승진");

		MemberResponse response = service.me(id);

		assertThat(response.memberId()).isEqualTo(id);
		assertThat(response.nickname()).isEqualTo("승진");
		assertThat(response.email()).isEqualTo("m@test.com");
		assertThat(response.provider()).isEqualTo(SocialProvider.GOOGLE);
		assertThat(response.createdAt()).isNotNull();
	}

	@Test
	@DisplayName("닉네임을 바꾸면 다듬어서 저장하고 다시 조회해도 같다")
	void changesNickname() {
		long id = member("a", "승진");

		MemberResponse changed = service.changeNickname(id, "  새 닉네임  ");

		assertThat(changed.nickname()).isEqualTo("새 닉네임");
		assertThat(service.me(id).nickname()).isEqualTo("새 닉네임");
	}

	@Test
	@DisplayName("없는 회원이면 MEMBER-0404다")
	void missingMember() {
		assertThatThrownBy(() -> service.me(999_999_999L)).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.NOT_FOUND));
		assertThatThrownBy(() -> service.changeNickname(999_999_999L, "x")).isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("가입할 때 닉네임이 없으면 기본값을, 너무 길면 잘라서 쓴다")
	void signUpNormalizesNickname() {
		assertThat(Member.signUp(SocialProvider.KAKAO, "1", null, null).getNickname()).isEqualTo("사용자");
		assertThat(Member.signUp(SocialProvider.KAKAO, "1", " ", null).getNickname()).isEqualTo("사용자");
		assertThat(Member.signUp(SocialProvider.KAKAO, "1", "가".repeat(40), null).getNickname()).hasSize(30);
		assertThat(Member.signUp(SocialProvider.KAKAO, "1", "n", "  ").getEmail()).isNull();
	}
}
