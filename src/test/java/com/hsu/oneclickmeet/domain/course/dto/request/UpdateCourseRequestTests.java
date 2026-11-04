package com.hsu.oneclickmeet.domain.course.dto.request;

import com.hsu.oneclickmeet.domain.course.dto.request.UpdateCourseRequest.MenuRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.UpdateCourseRequest.PlaceRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateCourseRequestTests {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();
	private static final String VALID_JSON = """
		{
		  "courseName": " 코스 ",
		  "places": [
		    {"coursePlaceId":null,"placeId":1,"menuList":[{"menuId":null,"menuName":" 음료 ","price":1000,"selected":false}]},
		    {"coursePlaceId":20,"placeId":2,"menuList":[]},
		    {"coursePlaceId":30,"placeId":3,"menuList":[]}
		  ]
		}
		""";

	@Test
	void 신규_장소와_메뉴의_명시적_null_ID를_받는다() {
		var request = MAPPER.readValue(VALID_JSON, UpdateCourseRequest.class);
		var place = request.places().getFirst();
		var menu = place.menuList().getFirst();

		assertThat(request.courseName()).isEqualTo("코스");
		assertThat(place.coursePlaceId()).isNull();
		assertThat(menu.menuId()).isNull();
		assertThat(menu.menuName()).isEqualTo("음료");
		try (var validator = Validation.buildDefaultValidatorFactory()) {
			assertThat(validator.getValidator().validate(request)).isEmpty();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"\"coursePlaceId\":null,", "\"menuId\":null,"})
	void 신규_ID_필드의_누락은_거절한다(String field) {
		assertThatThrownBy(() -> MAPPER.readValue(VALID_JSON.replace(field, ""), UpdateCourseRequest.class))
			.isInstanceOf(DatabindException.class);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"\"coursePlaceId\":20|\"coursePlaceId\":\"20\"",
		"\"menuId\":null|\"menuId\":1.5",
		"\"price\":1000|\"price\":\"1000\"",
		"\"selected\":false|\"selected\":\"false\""
	})
	void JSON_스칼라_타입을_검증한다(String field, String invalidField) {
		assertThatThrownBy(() -> MAPPER.readValue(VALID_JSON.replace(field, invalidField), UpdateCourseRequest.class))
			.isInstanceOf(DatabindException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"placeId", "coursePlaceId", "menuId"})
	void 장소_코스_구성_메뉴_ID의_중복을_거절한다(String duplicateField) {
		var firstMenu = new MenuRequest(100L, "음료", 1000, false);
		var secondMenu = new MenuRequest(duplicateField.equals("menuId") ? 100L : 200L, "디저트", 2000, true);
		var request = new UpdateCourseRequest("코스", List.of(
			new PlaceRequest(10L, 1L, List.of(firstMenu)),
			new PlaceRequest(duplicateField.equals("coursePlaceId") ? 10L : 20L,
				duplicateField.equals("placeId") ? 1L : 2L, List.of(secondMenu)),
			new PlaceRequest(null, 3L, List.of())
		));

		try (var validator = Validation.buildDefaultValidatorFactory()) {
			assertThat(validator.getValidator().validate(request))
				.extracting(violation -> violation.getPropertyPath().toString())
				.containsExactly("places");
		}
	}
}
