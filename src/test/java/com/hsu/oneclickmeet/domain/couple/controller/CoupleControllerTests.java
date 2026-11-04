package com.hsu.oneclickmeet.domain.couple.controller;

import com.hsu.oneclickmeet.domain.couple.dto.response.ConnectionResponse;
import com.hsu.oneclickmeet.domain.couple.dto.response.CoupleResponse;
import com.hsu.oneclickmeet.domain.couple.dto.response.DisconnectionResponse;
import com.hsu.oneclickmeet.domain.couple.dto.response.InvitationResponse;
import com.hsu.oneclickmeet.domain.couple.dto.response.MyCoupleResponse;
import com.hsu.oneclickmeet.domain.couple.dto.response.MyInvitationResponse;
import com.hsu.oneclickmeet.domain.couple.dto.response.PartnerResponse;
import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;
import com.hsu.oneclickmeet.domain.couple.entity.InvitationPurpose;
import com.hsu.oneclickmeet.domain.couple.service.CoupleDisconnectionService;
import com.hsu.oneclickmeet.domain.couple.service.CoupleService;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CoupleController.class)
class CoupleControllerTests {

	private static final long MEMBER_ID = 7L;
	private static final UUID IDEMPOTENCY_KEY = UUID.fromString("78006d1c-1079-4ae1-898a-999d5562a2bf");
	private static final Instant CONNECTED_AT = Instant.parse("2026-10-31T09:00:00Z");
	private static final Instant EXPIRES_AT = Instant.parse("2026-11-01T09:00:00Z");

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private CoupleService coupleService;

	@MockitoBean
	private CoupleDisconnectionService disconnectionService;

	@Test
	void 인증된_회원의_관계와_상대_표시이름을_조회한다() throws Exception {
		when(coupleService.getMyCouple(MEMBER_ID)).thenReturn(new MyCoupleResponse(new CoupleResponse(
			10L, CoupleStatus.CONNECTED, new PartnerResponse(2L, "한솔"), CONNECTED_AT,
			null, null, null, false, false)));

		mvc.perform(get("/api/v1/couples/me").param("memberId", "999").with(member()))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.couple.coupleId").value(10))
			.andExpect(jsonPath("$.data.couple.status").value("CONNECTED"))
			.andExpect(jsonPath("$.data.couple.partner.memberId").value(2))
			.andExpect(jsonPath("$.data.couple.partner.displayName").value("한솔"))
			.andExpect(jsonPath("$.data.couple.partner.email").doesNotHaveJsonPath())
			.andExpect(jsonPath("$.data.couple.connectedAt").value(CONNECTED_AT.toString()))
			.andExpect(jsonPath("$.data.couple.graceEndsAt").value(nullValue()))
			.andExpect(jsonPath("$.data.couple.sharedReadOnly").value(false))
			.andExpect(jsonPath("$.data.couple.canReconnect").value(false))
			.andExpect(jsonPath("$.error").value(nullValue()));
	}

	@Test
	void 이용할_관계가_없으면_couple을_null로_반환한다() throws Exception {
		when(coupleService.getMyCouple(MEMBER_ID)).thenReturn(new MyCoupleResponse(null));

		mvc.perform(get("/api/v1/couples/me").with(member()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.couple").value(nullValue()));
	}

	@Test
	void 연결_초대_발급은_201과_초대_객체를_반환한다() throws Exception {
		InvitationResponse invitation = new InvitationResponse(31L, InvitationPurpose.CONNECT, "KR7-4F2Q", null,
			EXPIRES_AT);
		when(coupleService.issueInvitation(MEMBER_ID, InvitationPurpose.CONNECT)).thenReturn(invitation);

		mvc.perform(post("/api/v1/couple-invitations").with(member()).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"purpose\":\"CONNECT\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.invitationId").value(31))
			.andExpect(jsonPath("$.data.purpose").value("CONNECT"))
			.andExpect(jsonPath("$.data.invitationCode").value("KR7-4F2Q"))
			.andExpect(jsonPath("$.data.coupleId").value(nullValue()))
			.andExpect(jsonPath("$.data.expiresAt").value(EXPIRES_AT.toString()));
	}

	@Test
	void 활성_초대를_지정한_목적으로_조회한다() throws Exception {
		InvitationResponse invitation = new InvitationResponse(31L, InvitationPurpose.RECONNECT, "KR7-4F2Q", 10L,
			EXPIRES_AT);
		when(coupleService.getMyInvitation(MEMBER_ID, InvitationPurpose.RECONNECT))
			.thenReturn(new MyInvitationResponse(invitation));

		mvc.perform(get("/api/v1/couple-invitations/me").param("purpose", "RECONNECT").with(member()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.invitation.invitationId").value(31))
			.andExpect(jsonPath("$.data.invitation.purpose").value("RECONNECT"))
			.andExpect(jsonPath("$.data.invitation.coupleId").value(10));
	}

	@Test
	void 활성_초대가_없으면_invitation을_null로_반환한다() throws Exception {
		when(coupleService.getMyInvitation(MEMBER_ID, InvitationPurpose.CONNECT))
			.thenReturn(new MyInvitationResponse(null));

		mvc.perform(get("/api/v1/couple-invitations/me").param("purpose", "CONNECT").with(member()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.invitation").value(nullValue()));
	}

	@Test
	void 최초_연결은_멱등성_키와_코드를_서비스에_전달하고_201을_반환한다() throws Exception {
		when(coupleService.connect(MEMBER_ID, IDEMPOTENCY_KEY, " kr7-4f2q "))
			.thenReturn(new ConnectionResponse(10L, CoupleStatus.CONNECTED));

		mvc.perform(post("/api/v1/couple-connections").with(member()).with(csrf())
			.header("Idempotency-Key", IDEMPOTENCY_KEY.toString())
			.contentType(MediaType.APPLICATION_JSON).content("{\"invitationCode\":\" kr7-4f2q \"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.coupleId").value(10))
			.andExpect(jsonPath("$.data.status").value("CONNECTED"));
	}

	@Test
	void 관계_해제는_본문_없이_받고_유예_시작과_종료_시각을_반환한다() throws Exception {
		Instant graceEndsAt = Instant.parse("2026-12-01T09:00:00Z");
		when(disconnectionService.disconnect(MEMBER_ID, 10L, IDEMPOTENCY_KEY, false))
			.thenReturn(new DisconnectionResponse(10L, CoupleStatus.GRACE, EXPIRES_AT, graceEndsAt));

		mvc.perform(post("/api/v1/couples/10/disconnections").with(member()).with(csrf())
			.header("Idempotency-Key", IDEMPOTENCY_KEY.toString()))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.coupleId").value(10))
			.andExpect(jsonPath("$.data.status").value("GRACE"))
			.andExpect(jsonPath("$.data.disconnectedAt").value(EXPIRES_AT.toString()))
			.andExpect(jsonPath("$.data.graceEndsAt").value(graceEndsAt.toString()))
			.andExpect(jsonPath("$.error").value(nullValue()));
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "-1", "invalid", "9223372036854775808"})
	void 관계_ID가_양의_long이_아니면_400을_반환한다(String coupleId) throws Exception {
		mvc.perform(post("/api/v1/couples/{coupleId}/disconnections", coupleId).with(member()).with(csrf())
			.header("Idempotency-Key", IDEMPOTENCY_KEY.toString()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		verifyNoInteractions(disconnectionService);
	}

	@Test
	void 관계_해제에_멱등성_키가_빠지면_400을_반환한다() throws Exception {
		mvc.perform(post("/api/v1/couples/10/disconnections").with(member()).with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		verifyNoInteractions(disconnectionService);
	}

	@Test
	void 관계_해제에_축약_UUID를_보내면_400을_반환한다() throws Exception {
		mvc.perform(post("/api/v1/couples/10/disconnections").with(member()).with(csrf())
			.header("Idempotency-Key", "1-2-3-4-5"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"))
			.andExpect(jsonPath("$.error.errors[0].field").value("Idempotency-Key"));
		verifyNoInteractions(disconnectionService);
	}

	@Test
	void 관계_해제에_본문이_있으면_서비스에_전달한다() throws Exception {
		when(disconnectionService.disconnect(MEMBER_ID, 10L, IDEMPOTENCY_KEY, true))
			.thenThrow(new BusinessException(CommonErrorCode.BAD_REQUEST));

		mvc.perform(post("/api/v1/couples/10/disconnections").with(member()).with(csrf())
			.header("Idempotency-Key", IDEMPOTENCY_KEY.toString()).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
	}

	@Test
	void 관계_해제는_인증된_회원_ID가_없으면_401을_반환한다() throws Exception {
		mvc.perform(post("/api/v1/couples/10/disconnections").with(user("7")).with(csrf())
			.header("Idempotency-Key", IDEMPOTENCY_KEY.toString()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("COMMON-0401"));
		verifyNoInteractions(disconnectionService);
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"purpose\":\"OTHER\"}", "{\"purpose\":0}"})
	void 초대_목적이_잘못되면_400을_반환한다(String body) throws Exception {
		mvc.perform(post("/api/v1/couple-invitations").with(member()).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		verifyNoInteractions(coupleService);
	}

	@Test
	void 활성_초대_조회에_목적이_빠지면_400을_반환한다() throws Exception {
		mvc.perform(get("/api/v1/couple-invitations/me").with(member()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		verifyNoInteractions(coupleService);
	}

	@Test
	void 최초_연결에_멱등성_키가_빠지면_400을_반환한다() throws Exception {
		mvc.perform(post("/api/v1/couple-connections").with(member()).with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"invitationCode\":\"KR7-4F2Q\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		verifyNoInteractions(coupleService);
	}

	@Test
	void 멱등성_키에_축약_UUID를_보내면_400을_반환한다() throws Exception {
		mvc.perform(post("/api/v1/couple-connections").with(member()).with(csrf())
			.header("Idempotency-Key", "1-2-3-4-5")
			.contentType(MediaType.APPLICATION_JSON).content("{\"invitationCode\":\"KR7-4F2Q\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"))
			.andExpect(jsonPath("$.error.errors[0].field").value("Idempotency-Key"));
		verifyNoInteractions(coupleService);
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"invitationCode\":1234567}"})
	void 문자열_초대_코드가_없는_최초_연결에_400을_반환한다(String body) throws Exception {
		mvc.perform(post("/api/v1/couple-connections").with(member()).with(csrf())
			.header("Idempotency-Key", IDEMPOTENCY_KEY.toString())
			.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"))
			.andExpect(jsonPath("$.error.errors[0].field").value("invitationCode"));
		verifyNoInteractions(coupleService);
	}

	@Test
	void 로그인_이름과_요청_값으로_회원_ID를_대체할_수_없다() throws Exception {
		mvc.perform(get("/api/v1/couples/me").param("memberId", "7").with(user("7")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("COMMON-0401"));
		verifyNoInteractions(coupleService);
	}

	private RequestPostProcessor member() {
		return authentication(UsernamePasswordAuthenticationToken.authenticated(MEMBER_ID, null, List.of()));
	}
}
