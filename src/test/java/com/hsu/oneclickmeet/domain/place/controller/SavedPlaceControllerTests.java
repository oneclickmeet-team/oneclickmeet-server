package com.hsu.oneclickmeet.domain.place.controller;

import com.hsu.oneclickmeet.domain.place.dto.request.SaveSavedPlaceRequest;
import com.hsu.oneclickmeet.domain.place.dto.response.SavedPlaceListResponse;
import com.hsu.oneclickmeet.domain.place.dto.response.SavedPlaceResponse;
import com.hsu.oneclickmeet.domain.place.exception.PlaceErrorCode;
import com.hsu.oneclickmeet.domain.place.service.SavedPlaceService;
import com.hsu.oneclickmeet.domain.place.service.SavedPlaceService.SaveResult;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SavedPlaceController.class)
class SavedPlaceControllerTests {

	private static final String URL = "/api/v1/saved-places";
	private static final long MEMBER_ID = 7L;
	private static final Instant SAVED_AT = Instant.parse("2026-11-05T03:00:00Z");
	private static final String BODY = """
		{"providerPlaceId":"123","name":"예시 카페","address":"서울 성북구 삼선동 1","roadAddress":"서울 성북구 한성로 12",
		 "category":"음식점 > 카페","categoryGroupCode":"CE7","lat":37.5822,"lng":127.0105,
		 "placeUrl":"http://place.map.kakao.com/123","memberId":999}
		""";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private SavedPlaceService service;

	private static SavedPlaceResponse response(long id) {
		return new SavedPlaceResponse(id, "123", "예시 카페", "서울 성북구 삼선동 1", "서울 성북구 한성로 12",
				"음식점 > 카페", "CE7", 37.5822, 127.0105, "http://place.map.kakao.com/123", SAVED_AT);
	}

	private static RequestPostProcessor member() {
		return authentication(UsernamePasswordAuthenticationToken.authenticated(MEMBER_ID, null, List.of()));
	}

	@Test
	@DisplayName("새로 저장하면 인증된 회원 번호로 서비스를 호출하고 201을 돌려준다")
	void createsWithAuthenticatedMemberAndReturns201() throws Exception {
		when(service.save(eq(MEMBER_ID), any(SaveSavedPlaceRequest.class))).thenReturn(new SaveResult(response(11), true));

		mvc.perform(post(URL).with(member()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.savedPlaceId").value(11))
			.andExpect(jsonPath("$.data.providerPlaceId").value("123"))
			.andExpect(jsonPath("$.data.name").value("예시 카페"))
			.andExpect(jsonPath("$.data.savedAt").value(SAVED_AT.toString()))
			.andExpect(jsonPath("$.data.memberId").doesNotHaveJsonPath())
			.andExpect(jsonPath("$.error").value(nullValue()));
		verify(service).save(MEMBER_ID, new SaveSavedPlaceRequest("123", "예시 카페", "서울 성북구 삼선동 1", "서울 성북구 한성로 12",
				"음식점 > 카페", "CE7", 37.5822, 127.0105, "http://place.map.kakao.com/123"));
	}

	@Test
	@DisplayName("이미 저장한 장소를 다시 저장하면 200과 기존 항목을 돌려준다")
	void returns200WhenAlreadySaved() throws Exception {
		when(service.save(eq(MEMBER_ID), any(SaveSavedPlaceRequest.class))).thenReturn(new SaveResult(response(11), false));

		mvc.perform(post(URL).with(member()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.savedPlaceId").value(11));
	}

	@Test
	@DisplayName("요청 본문의 값이 잘못되면 400과 필드별 오류를 돌려주고 서비스를 호출하지 않는다")
	void rejectsInvalidBody() throws Exception {
		String invalid = """
			{"providerPlaceId":" ","name":"","address":null,"lat":91,"lng":null}
			""";

		mvc.perform(post(URL).with(member()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalid))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"))
			.andExpect(jsonPath("$.error.errors[*].field").value(hasItem("providerPlaceId")))
			.andExpect(jsonPath("$.error.errors[*].field").value(hasItem("name")))
			.andExpect(jsonPath("$.error.errors[*].field").value(hasItem("address")))
			.andExpect(jsonPath("$.error.errors[*].field").value(hasItem("lat")))
			.andExpect(jsonPath("$.error.errors[*].field").value(hasItem("lng")));
		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("로그인하지 않으면 401이고 서비스를 호출하지 않는다")
	void rejectsUnauthenticatedRequests() throws Exception {
		mvc.perform(get(URL).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
		mvc.perform(get(URL + "/1").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
		mvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
		mvc.perform(delete(URL + "/1").with(csrf())).andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("목록은 기본 20건으로 조회하고 다음 커서를 돌려준다")
	void listsWithDefaultSize() throws Exception {
		when(service.list(MEMBER_ID, null, 20, null))
			.thenReturn(new SavedPlaceListResponse(List.of(response(12), response(11)), "next-cursor", true));

		mvc.perform(get(URL).with(member()).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].savedPlaceId").value(12))
			.andExpect(jsonPath("$.data.items[1].savedPlaceId").value(11))
			.andExpect(jsonPath("$.data.nextCursor").value("next-cursor"))
			.andExpect(jsonPath("$.data.hasNext").value(true));
	}

	@Test
	@DisplayName("목록의 커서·size·분류를 서비스로 전달한다")
	void passesCursorSizeAndCategory() throws Exception {
		when(service.list(MEMBER_ID, "abc", 5, "CE7")).thenReturn(new SavedPlaceListResponse(List.of(), null, false));

		mvc.perform(get(URL).param("cursor", "abc").param("size", "5").param("categoryGroupCode", "CE7").with(member()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items").isEmpty())
			.andExpect(jsonPath("$.data.hasNext").value(false));
		verify(service).list(MEMBER_ID, "abc", 5, "CE7");
	}

	@ParameterizedTest(name = "size={0}은 거절한다")
	@ValueSource(strings = {"0", "101", "-1", "abc", "1.5"})
	@DisplayName("size가 1~100이 아니면 400이다")
	void rejectsInvalidSize(String size) throws Exception {
		mvc.perform(get(URL).param("size", size).with(member()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON-0400"));
		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("상세는 본인 번호로 조회하고 없으면 PLACE-0404 404다")
	void detailReturnsPlaceOrNotFound() throws Exception {
		when(service.detail(MEMBER_ID, 11L)).thenReturn(response(11));
		when(service.detail(MEMBER_ID, 12L)).thenThrow(new BusinessException(PlaceErrorCode.NOT_FOUND));

		mvc.perform(get(URL + "/11").with(member()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.savedPlaceId").value(11));
		mvc.perform(get(URL + "/12").with(member()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("PLACE-0404"));
	}

	@ParameterizedTest(name = "savedPlaceId={0}은 거절한다")
	@ValueSource(strings = {"0", "-1", "abc", "1.5"})
	@DisplayName("savedPlaceId가 양의 정수가 아니면 400이다")
	void rejectsInvalidId(String id) throws Exception {
		mvc.perform(get(URL + "/" + id).with(member())).andExpect(status().isBadRequest());
		mvc.perform(delete(URL + "/" + id).with(member()).with(csrf())).andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("삭제는 본인 번호로 서비스를 호출하고 204를 돌려준다")
	void deleteReturns204() throws Exception {
		mvc.perform(delete(URL + "/11").with(member()).with(csrf()))
			.andExpect(status().isNoContent());
		verify(service).delete(MEMBER_ID, 11L);
	}

	@Test
	@DisplayName("삭제할 장소가 없거나 다른 회원의 것이면 404다")
	void deleteReturns404WhenMissing() throws Exception {
		org.mockito.Mockito.doThrow(new BusinessException(PlaceErrorCode.NOT_FOUND)).when(service).delete(anyLong(), anyLong());

		mvc.perform(delete(URL + "/99").with(member()).with(csrf()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("PLACE-0404"));
	}

	@Test
	@DisplayName("인증 정보의 회원 번호가 올바르지 않으면 401이다")
	void rejectsInvalidPrincipal() throws Exception {
		RequestPostProcessor invalid = authentication(UsernamePasswordAuthenticationToken.authenticated(-5L, null, List.of()));

		mvc.perform(get(URL).with(invalid))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("COMMON-0401"));
		verify(service, org.mockito.Mockito.never()).list(anyLong(), any(), anyInt(), any());
	}
}
