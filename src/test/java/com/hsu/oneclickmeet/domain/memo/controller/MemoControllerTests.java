package com.hsu.oneclickmeet.domain.memo.controller;

import com.hsu.oneclickmeet.domain.memo.dto.request.CreateMemoRequest;
import com.hsu.oneclickmeet.domain.memo.dto.response.MemoListResponse;
import com.hsu.oneclickmeet.domain.memo.dto.response.MemoResponse;
import com.hsu.oneclickmeet.domain.memo.entity.MemoTargetType;
import com.hsu.oneclickmeet.domain.memo.exception.MemoErrorCode;
import com.hsu.oneclickmeet.domain.memo.service.MemoService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemoController.class)
class MemoControllerTests {

	private static final String MEMOS_URL = "/api/v1/memos";
	private static final long MEMBER_ID = 7L;
	private static final long MEMO_ID = 301L;
	private static final long TARGET_ID = 101L;
	private static final UUID KEY = UUID.fromString("78006d1c-1079-4ae1-898a-999d5562a2bf");
	private static final Instant CREATED_AT = Instant.parse("2026-11-04T03:00:00Z");
	private static final String CREATE_BODY = """
		{"targetType":"COURSE","targetId":101,"content":"웨이팅이 길면 카페부터 가기","authorId":999}
		""";
	private static final String INITIAL_CONTENT = "웨이팅이 길면 카페부터 가기";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private MemoService memoService;

	@Test
	void 메모를_작성하면_principal과_멱등성_키를_전달하고_201을_반환한다() throws Exception {
		when(memoService.create(MEMBER_ID, KEY, new CreateMemoRequest(MemoTargetType.COURSE, TARGET_ID, INITIAL_CONTENT)))
			.thenReturn(memo(INITIAL_CONTENT, CREATED_AT));

		mvc.perform(post(MEMOS_URL).with(member()).with(csrf()).header("Idempotency-Key", KEY.toString())
			.contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.memoId").value(MEMO_ID))
			.andExpect(jsonPath("$.data.targetType").value("COURSE"))
			.andExpect(jsonPath("$.data.targetId").value(TARGET_ID))
			.andExpect(jsonPath("$.data.content").value(INITIAL_CONTENT))
			.andExpect(jsonPath("$.data.createdAt").value(CREATED_AT.toString()))
			.andExpect(jsonPath("$.data.updatedAt").value(CREATED_AT.toString()))
			.andExpect(jsonPath("$.data.authorId").doesNotHaveJsonPath())
			.andExpect(jsonPath("$.error").value(nullValue()));
	}

	@Test
	void 생성_요청에_멱등성_키가_없거나_축약_UUID면_400을_반환한다() throws Exception {
		mvc.perform(post(MEMOS_URL).with(member()).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		mvc.perform(post(MEMOS_URL).with(member()).with(csrf()).header("Idempotency-Key", "1-2-3-4-5")
			.contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		verifyNoInteractions(memoService);
	}

	@ParameterizedTest
	@CsvSource({"IDEMPOTENCY_KEY_CONFLICT,MEMO-0409-01", "CREATE_IN_PROGRESS,MEMO-0409-02"})
	void 같은_멱등성_키의_충돌과_처리_중_응답을_구분한다(MemoErrorCode errorCode, String code) throws Exception {
		when(memoService.create(MEMBER_ID, KEY, new CreateMemoRequest(MemoTargetType.COURSE, TARGET_ID, INITIAL_CONTENT)))
			.thenThrow(new BusinessException(errorCode));

		mvc.perform(post(MEMOS_URL).with(member()).with(csrf()).header("Idempotency-Key", KEY.toString())
			.contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.data").value(nullValue()))
			.andExpect(jsonPath("$.error.code").value(code));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{}",
		"{\"targetType\":0,\"targetId\":101,\"content\":\"메모\"}",
		"{\"targetType\":\"OTHER\",\"targetId\":101,\"content\":\"메모\"}",
		"{\"targetType\":\"COURSE\",\"targetId\":\"101\",\"content\":\"메모\"}",
		"{\"targetType\":\"COURSE\",\"targetId\":101.0,\"content\":\"메모\"}",
		"{\"targetType\":\"COURSE\",\"targetId\":0,\"content\":\"메모\"}",
		"{\"targetType\":\"COURSE\",\"targetId\":9223372036854775808,\"content\":\"메모\"}",
		"{\"targetType\":\"COURSE\",\"targetId\":101,\"content\":123}",
		"{\"targetType\":\"COURSE\",\"targetId\":101,\"content\":\"\\uD800\"}"
	})
	void 생성_본문이_유효하지_않으면_400을_반환한다(String body) throws Exception {
		mvc.perform(post(MEMOS_URL).with(member()).with(csrf()).header("Idempotency-Key", KEY.toString())
			.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		verifyNoInteractions(memoService);
	}

	@Test
	void 필터를_생략하면_기본_크기로_조회하고_빈_목록을_반환한다() throws Exception {
		when(memoService.list(MEMBER_ID, null, null, null, 20))
			.thenReturn(new MemoListResponse(List.of(), null, false));

		mvc.perform(get(MEMOS_URL).with(member()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items").isEmpty())
			.andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
			.andExpect(jsonPath("$.data.hasNext").value(false));
	}

	@Test
	void 대상_필터로_조회하면_해당_메모를_반환한다() throws Exception {
		when(memoService.list(MEMBER_ID, MemoTargetType.COURSE, TARGET_ID, null, 20))
			.thenReturn(new MemoListResponse(List.of(memo(INITIAL_CONTENT, CREATED_AT)), null, false));

		mvc.perform(get(MEMOS_URL).with(member()).param("targetType", "COURSE").param("targetId", "101"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].memoId").value(MEMO_ID))
			.andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
			.andExpect(jsonPath("$.data.hasNext").value(false));
	}

	@Test
	void 전체_목록_조회에_커서와_크기를_전달한다() throws Exception {
		when(memoService.list(MEMBER_ID, null, null, "previous-cursor", 100))
			.thenReturn(new MemoListResponse(List.of(memo(INITIAL_CONTENT, CREATED_AT)), "next-cursor", true));

		mvc.perform(get(MEMOS_URL).with(member()).param("cursor", "previous-cursor").param("size", "100"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].memoId").value(MEMO_ID))
			.andExpect(jsonPath("$.data.nextCursor").value("next-cursor"))
			.andExpect(jsonPath("$.data.hasNext").value(true));
	}

	@ParameterizedTest
	@CsvSource({"COURSE,,20", ",101,20", "OTHER,101,20", "COURSE,0,20", "COURSE,101,101"})
	void 필터_짝이나_페이지_크기가_잘못되면_400을_반환한다(String type, String id, String size) throws Exception {
		var request = get(MEMOS_URL).with(member()).param("size", size);
		if (type != null) {
			request.param("targetType", type);
		}
		if (id != null) {
			request.param("targetId", id);
		}
		mvc.perform(request)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		verifyNoInteractions(memoService);
	}

	@Test
	void 상세는_인증_회원과_메모_ID로_조회한다() throws Exception {
		when(memoService.detail(MEMBER_ID, MEMO_ID)).thenReturn(memo(INITIAL_CONTENT, CREATED_AT));

		mvc.perform(get(MEMOS_URL + "/{memoId}", MEMO_ID).with(member()).param("memberId", "999"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.memoId").value(MEMO_ID))
			.andExpect(jsonPath("$.data.content").value(INITIAL_CONTENT));
	}

	@Test
	void 접근할_수_없는_메모는_404를_반환한다() throws Exception {
		when(memoService.detail(MEMBER_ID, MEMO_ID)).thenThrow(new BusinessException(MemoErrorCode.NOT_FOUND));

		mvc.perform(get(MEMOS_URL + "/{memoId}", MEMO_ID).with(member()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.data").value(nullValue()))
			.andExpect(jsonPath("$.error.code").value("MEMO-0404"));
	}

	@Test
	void 수정_요청에서_메모_내용_2천자를_그대로_전달한다() throws Exception {
		String updatedContent = " 🙂" + "가".repeat(1_997) + " ";
		Instant updatedAt = CREATED_AT.plusSeconds(60);
		when(memoService.update(MEMBER_ID, MEMO_ID, updatedContent)).thenReturn(memo(updatedContent, updatedAt));

		mvc.perform(patch(MEMOS_URL + "/{memoId}", MEMO_ID).with(member()).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + updatedContent + "\",\"targetId\":999}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.targetId").value(TARGET_ID))
			.andExpect(jsonPath("$.data.content").value(updatedContent))
			.andExpect(jsonPath("$.data.createdAt").value(CREATED_AT.toString()))
			.andExpect(jsonPath("$.data.updatedAt").value(updatedAt.toString()));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " \\n\\t", "\\u00a0"})
	void 수정_내용이_비었거나_공백이면_400을_반환한다(String blankContent) throws Exception {
		mvc.perform(patch(MEMOS_URL + "/{memoId}", MEMO_ID).with(member()).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + blankContent + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"))
			.andExpect(jsonPath("$.error.errors[0].field").value("content"));
		verifyNoInteractions(memoService);
	}

	@Test
	void 생성_내용이_2천자를_넘으면_400을_반환한다() throws Exception {
		String body = "{\"targetType\":\"COURSE\",\"targetId\":101,\"content\":\"" + "가".repeat(2_001) + "\"}";
		mvc.perform(post(MEMOS_URL).with(member()).with(csrf()).header("Idempotency-Key", KEY.toString())
			.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"))
			.andExpect(jsonPath("$.error.errors[0].field").value("content"));
		verifyNoInteractions(memoService);
	}

	@Test
	void 삭제는_본문_없이_204를_반환한다() throws Exception {
		mvc.perform(delete(MEMOS_URL + "/{memoId}", MEMO_ID).with(member()).with(csrf()))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));
		verify(memoService).delete(MEMBER_ID, MEMO_ID);
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "0x12d", "9223372036854775808"})
	void 유효하지_않은_경로_ID는_400을_반환한다(String invalidId) throws Exception {
		mvc.perform(get(MEMOS_URL + "/{memoId}", invalidId).with(member()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		verifyNoInteractions(memoService);
	}

	@Test
	void 로그인_이름이나_요청_값으로_메모_작성자를_정할_수_없다() throws Exception {
		mvc.perform(get(MEMOS_URL).param("memberId", "7").with(user("7")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("COMMON-0401"));
		verifyNoInteractions(memoService);
	}

	@Test
	void 인증하지_않으면_메모를_조회할_수_없다() throws Exception {
		mvc.perform(get(MEMOS_URL).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isUnauthorized());
		verifyNoInteractions(memoService);
	}

	private MemoResponse memo(String content, Instant updatedAt) {
		return new MemoResponse(MEMO_ID, MemoTargetType.COURSE, TARGET_ID, content, CREATED_AT, updatedAt);
	}

	private RequestPostProcessor member() {
		return authentication(UsernamePasswordAuthenticationToken.authenticated(MEMBER_ID, null, List.of()));
	}
}
