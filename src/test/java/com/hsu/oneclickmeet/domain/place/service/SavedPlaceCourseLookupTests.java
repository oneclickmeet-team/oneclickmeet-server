package com.hsu.oneclickmeet.domain.place.service;

import com.hsu.oneclickmeet.domain.course.service.CoursePlaceLookup;
import com.hsu.oneclickmeet.domain.place.dto.request.SaveSavedPlaceRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SavedPlaceCourseLookupTests {

	private static final long MEMBER = 830_001L;
	private static final long OTHER = 830_002L;

	@Autowired
	private CoursePlaceLookup lookup;
	@Autowired
	private SavedPlaceService service;
	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from saved_places where member_id in (?, ?)", MEMBER, OTHER);
	}

	@Test
	@DisplayName("코스가 쓰는 장소 조회는 장소 도메인의 구현이다")
	void lookupBeanIsProvidedByPlaceDomain() {
		assertThat(lookup).isInstanceOf(SavedPlaceCourseLookup.class);
	}

	@Test
	@DisplayName("본인의 보관함 장소면 이름을 돌려준다")
	void returnsNameForOwnPlace() {
		long id = service.save(MEMBER, new SaveSavedPlaceRequest("100", "예시 카페", "주소", null, null, null, 37.5, 127.0, null, null))
				.place().savedPlaceId();

		assertThat(lookup.findPlaceName(MEMBER, id)).contains("예시 카페");
	}

	@Test
	@DisplayName("다른 회원의 장소나 없는 장소는 비어 있다")
	void returnsEmptyForOtherMembersOrMissingPlace() {
		long id = service.save(MEMBER, new SaveSavedPlaceRequest("100", "예시 카페", "주소", null, null, null, 37.5, 127.0, null, null))
				.place().savedPlaceId();

		assertThat(lookup.findPlaceName(OTHER, id)).isEmpty();
		assertThat(lookup.findPlaceName(MEMBER, id + 1_000_000)).isEmpty();
	}
}
