package com.hsu.oneclickmeet.domain.place.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceSearchRequestTests {

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void setUp() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void tearDown() {
		factory.close();
	}

	private static Set<ConstraintViolation<PlaceSearchRequest>> validate(PlaceSearchRequest request) {
		return validator.validate(request);
	}

	@Test
	@DisplayName("검색어만 있으면 page=1, size=10 기본값으로 유효하다")
	void validWithDefaults() {
		PlaceSearchRequest request = new PlaceSearchRequest("카페", null, null);

		assertThat(validate(request)).isEmpty();
		assertThat(request.page()).isEqualTo(1);
		assertThat(request.size()).isEqualTo(10);
	}

	@ParameterizedTest(name = "검색어가 비어 있으면 거절한다: [{0}]")
	@ValueSource(strings = {"", "   "})
	@DisplayName("검색어가 비어 있거나 공백뿐이면 거절한다")
	void rejectsBlankQuery(String query) {
		assertThat(validate(new PlaceSearchRequest(query, 1, 10))).isNotEmpty();
	}

	@Test
	@DisplayName("검색어가 null이면 거절한다")
	void rejectsNullQuery() {
		assertThat(validate(new PlaceSearchRequest(null, 1, 10))).isNotEmpty();
	}

	@Test
	@DisplayName("검색어는 100자까지 허용하고 101자부터 거절한다")
	void limitsQueryLength() {
		assertThat(validate(new PlaceSearchRequest("가".repeat(100), 1, 10))).isEmpty();
		assertThat(validate(new PlaceSearchRequest("가".repeat(101), 1, 10))).isNotEmpty();
	}

	@Test
	@DisplayName("page는 1 이상이어야 한다")
	void rejectsPageBelowOne() {
		assertThat(validate(new PlaceSearchRequest("카페", 0, 10))).isNotEmpty();
		assertThat(validate(new PlaceSearchRequest("카페", 1, 10))).isEmpty();
	}

	@Test
	@DisplayName("size는 1~15만 허용한다")
	void limitsSizeRange() {
		assertThat(validate(new PlaceSearchRequest("카페", 1, 0))).isNotEmpty();
		assertThat(validate(new PlaceSearchRequest("카페", 1, 16))).isNotEmpty();
		assertThat(validate(new PlaceSearchRequest("카페", 1, 1))).isEmpty();
		assertThat(validate(new PlaceSearchRequest("카페", 1, 15))).isEmpty();
	}
}
