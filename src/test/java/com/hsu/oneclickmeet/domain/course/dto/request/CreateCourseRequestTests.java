package com.hsu.oneclickmeet.domain.course.dto.request;

import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.MenuRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.PlaceRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateCourseRequestTests {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();
	private static final String REQUEST_JSON = """
		{
		  "courseName": %s,
		  "places": [
		    {"placeId": %s, "menuList": [%s]},
		    {"placeId": 2, "menuList": []},
		    {"placeId": 3, "menuList": []}
		  ]
		}
		""";
	private static final String VALID_MENU = """
		{"menuId":null,"menuName":"음료","price":1000,"selected":false}
		""";
	private static ValidatorFactory validatorFactory;

	@BeforeAll
	static void startValidation() {
		validatorFactory = Validation.buildDefaultValidatorFactory();
	}

	@AfterAll
	static void closeValidation() {
		validatorFactory.close();
	}

	@ParameterizedTest
	@ValueSource(ints = {2, 3, 5, 6})
	void 장소는_세_개부터_다섯_개까지_받는다(int placeCount) {
		var places = IntStream.rangeClosed(1, placeCount)
			.mapToObj(id -> new PlaceRequest((long) id, List.of()))
			.toList();

		var errors = invalidFields(new CreateCourseRequest("코스", places));

		if (placeCount == 3 || placeCount == 5) {
			assertThat(errors).isEmpty();
		} else {
			assertThat(errors).containsExactly("places");
		}
	}

	@Test
	void 이름_양끝의_공백을_제거하고_코드_포인트로_길이를_검증한다() {
		String name = "😀".repeat(100);
		var valid = MAPPER.readValue(
			REQUEST_JSON.formatted("\"\u00A0 " + name + " \u3000\"", "1",
				VALID_MENU.replace("음료", "\u202F" + name + "\u2007")),
			CreateCourseRequest.class
		);

		assertThat(valid.courseName()).isEqualTo(name);
		assertThat(valid.places().getFirst().menuList().getFirst().menuName()).isEqualTo(name);
		assertThat(invalidFields(valid)).isEmpty();
		var invalid = new CreateCourseRequest(name + "😀", List.of(
			new PlaceRequest(1L, List.of(new MenuRequest(null, " \u00A0 ", 0, false))),
			new PlaceRequest(2L, List.of()),
			new PlaceRequest(3L, List.of())
		));
		assertThat(invalidFields(invalid)).containsExactlyInAnyOrder("courseName", "places[0].menuList[0].menuName");
	}

	@Test
	void 중복_장소와_null_목록_원소를_거절한다() {
		var request = new CreateCourseRequest("코스", Arrays.asList(
			new PlaceRequest(1L, Collections.singletonList(null)),
			null,
			new PlaceRequest(1L, List.of())
		));

		assertThat(invalidFields(request)).containsExactlyInAnyOrder(
			"places", "places[0].menuList[0].<list element>", "places[1].<list element>");
	}

	@Test
	void 메뉴_목록과_신규_메뉴의_필수값을_검증한다() {
		var menu = new MenuRequest(null, "음료", 1_000_000_000, false);
		var request = new CreateCourseRequest("코스", List.of(
			new PlaceRequest(0L, null),
			new PlaceRequest(2L, Collections.nCopies(51, menu)),
			new PlaceRequest(3L, List.of(new MenuRequest(1L, "음료", 1_000_000_001, null)))
		));

		assertThat(invalidFields(request)).containsExactlyInAnyOrder(
			"places[0].placeId", "places[0].menuList", "places[1].menuList",
			"places[2].menuList[0].menuId", "places[2].menuList[0].price", "places[2].menuList[0].selected");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{\"menuName\":\"음료\",\"price\":1000,\"selected\":true}",
		"{\"menuId\":\"\",\"menuName\":\"음료\",\"price\":1000,\"selected\":true}",
		"{\"menuId\":null,\"menuName\":\"음료\",\"price\":1000.5,\"selected\":true}",
		"{\"menuId\":null,\"menuName\":\"음료\",\"price\":\"1000\",\"selected\":true}",
		"{\"menuId\":null,\"menuName\":\"음료\",\"price\":1000,\"selected\":\"true\"}",
		"{\"menuId\":null,\"menuName\":12,\"price\":1000,\"selected\":true}"
	})
	void 메뉴_ID_누락과_잘못된_JSON_타입을_거절한다(String menu) {
		assertThatThrownBy(() -> MAPPER.readValue(
			REQUEST_JSON.formatted("\"코스\"", "1", menu), CreateCourseRequest.class))
			.isInstanceOf(DatabindException.class);
	}

	@Test
	void 숫자_이름과_문자열_장소_ID를_거절한다() {
		assertThatThrownBy(() -> MAPPER.readValue(
			REQUEST_JSON.formatted("1", "1", VALID_MENU), CreateCourseRequest.class))
			.isInstanceOf(DatabindException.class);
		assertThatThrownBy(() -> MAPPER.readValue(
			REQUEST_JSON.formatted("\"코스\"", "\"1\"", VALID_MENU), CreateCourseRequest.class))
			.isInstanceOf(DatabindException.class);
	}

	private static List<String> invalidFields(CreateCourseRequest request) {
		return validatorFactory.getValidator().validate(request).stream()
			.map(ConstraintViolation::getPropertyPath)
			.map(Object::toString)
			.toList();
	}
}
