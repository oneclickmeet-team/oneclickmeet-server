package com.hsu.oneclickmeet.domain.place.controller;

import com.hsu.oneclickmeet.domain.place.entity.PlaceProvider;
import com.hsu.oneclickmeet.domain.place.entity.SavedPlace;
import com.hsu.oneclickmeet.domain.place.repository.SavedPlaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SavedPlaceFavoriteIntegrationTests {

	@Autowired private MockMvc mvc;
	@Autowired private SavedPlaceRepository places;

	@Test
	void favoriteCanBeSetAndClearedOnlyByOwnerAndPersistsInList() throws Exception {
		long owner = 701001L;
		long id = places.save(SavedPlace.create(owner, PlaceProvider.KAKAO, "favorite-test", "장소", "주소",
				null, null, null, 37.5, 127.0, null)).getId();
		var ownerAuth = authentication(UsernamePasswordAuthenticationToken.authenticated(owner, null, List.of()));
		var otherAuth = authentication(UsernamePasswordAuthenticationToken.authenticated(701002L, null, List.of()));
		String url = "/api/v1/saved-places/" + id + "/favorite";
		mvc.perform(put(url).with(otherAuth).contentType(MediaType.APPLICATION_JSON).content("{\"favorite\":true}"))
				.andExpect(status().isNotFound());
		mvc.perform(put(url).with(ownerAuth).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/saved-places/{id}", id).with(ownerAuth))
				.andExpect(jsonPath("$.data.favorite").value(false));
		for (int i = 0; i < 2; i++) {
			mvc.perform(put(url).with(ownerAuth).contentType(MediaType.APPLICATION_JSON).content("{\"favorite\":true}"))
					.andExpect(status().isOk()).andExpect(jsonPath("$.data.favorite").value(true));
		}
		mvc.perform(get("/api/v1/saved-places").with(ownerAuth))
				.andExpect(jsonPath("$.data.items[0].favorite").value(true));
		mvc.perform(put(url).with(ownerAuth).contentType(MediaType.APPLICATION_JSON).content("{\"favorite\":false}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.favorite").value(false));
		mvc.perform(get("/api/v1/saved-places/{id}", id).with(ownerAuth))
				.andExpect(jsonPath("$.data.favorite").value(false));
	}
}
