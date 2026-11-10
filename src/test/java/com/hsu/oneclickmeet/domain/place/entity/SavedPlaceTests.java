package com.hsu.oneclickmeet.domain.place.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SavedPlaceTests {

	private static SavedPlace valid() {
		return SavedPlace.create(7L, PlaceProvider.KAKAO, " 123 ", " 예시 카페 ", " 서울 성북구 삼선동 1 ",
				"서울 성북구 한성로 12", "음식점 > 카페", "CE7", 37.5822, 127.0105, "http://place.map.kakao.com/123");
	}

	@Test
	@DisplayName("값을 다듬어서 생성하고 비어 있는 선택 값은 null로 둔다")
	void createsTrimmedPlaceAndNullsBlankOptionals() {
		SavedPlace place = SavedPlace.create(7L, PlaceProvider.KAKAO, " 123 ", " 예시 카페 ", " 주소 ", "  ", "", " ",
				37.5, 127.0, null);

		assertThat(place.getMemberId()).isEqualTo(7L);
		assertThat(place.getProvider()).isEqualTo(PlaceProvider.KAKAO);
		assertThat(place.getProviderPlaceId()).isEqualTo("123");
		assertThat(place.getName()).isEqualTo("예시 카페");
		assertThat(place.getAddress()).isEqualTo("주소");
		assertThat(place.getRoadAddress()).isNull();
		assertThat(place.getCategory()).isNull();
		assertThat(place.getCategoryGroupCode()).isNull();
		assertThat(place.getPlaceUrl()).isNull();
		assertThat(place.getId()).isNull();
	}

	@Test
	@DisplayName("태그는 다듬어서 보관하고, 없거나 비어 있으면 빈 목록이다")
	void keepsTrimmedTagsOrEmptyList() {
		SavedPlace tagged = SavedPlace.create(7L, PlaceProvider.KAKAO, "1", "카페", "주소", null, null, null, 37.5, 127.0,
				null, List.of(new SavedPlaceTag(" 아늑해요 ", 12)));

		assertThat(tagged.getTags()).containsExactly(new SavedPlaceTag("아늑해요", 12));
		assertThat(valid().getTags()).isEmpty();
		assertThat(SavedPlace.create(7L, PlaceProvider.KAKAO, "1", "카페", "주소", null, null, null, 37.5, 127.0, null,
				List.of()).getTags()).isEmpty();
	}

	@Test
	@DisplayName("태그가 6개 이상이거나 label이 비었거나 mentionCount가 음수면 생성할 수 없다")
	void rejectsInvalidTags() {
		SavedPlaceTag tag = new SavedPlaceTag("아늑해요", 1);
		for (List<SavedPlaceTag> invalid : List.of(
				List.of(tag, tag, tag, tag, tag, tag),
				List.of(new SavedPlaceTag(" ", 1)),
				List.of(new SavedPlaceTag("a".repeat(51), 1)),
				List.of(new SavedPlaceTag("아늑해요", -1)))) {
			assertThatIllegalArgumentException().isThrownBy(() -> SavedPlace.create(7L, PlaceProvider.KAKAO, "1", "카페",
					"주소", null, null, null, 37.5, 127.0, null, invalid));
		}
	}

	@Test
	@DisplayName("모든 값이 있으면 그대로 보관한다")
	void keepsAllValues() {
		SavedPlace place = valid();

		assertThat(place.getRoadAddress()).isEqualTo("서울 성북구 한성로 12");
		assertThat(place.getCategory()).isEqualTo("음식점 > 카페");
		assertThat(place.getCategoryGroupCode()).isEqualTo("CE7");
		assertThat(place.getLat()).isEqualTo(37.5822);
		assertThat(place.getLng()).isEqualTo(127.0105);
		assertThat(place.getPlaceUrl()).isEqualTo("http://place.map.kakao.com/123");
	}

	@ParameterizedTest(name = "memberId={0}은 거절한다")
	@ValueSource(longs = {0L, -1L})
	@DisplayName("회원 번호는 양수여야 한다")
	void rejectsNonPositiveMemberId(long memberId) {
		assertThatIllegalArgumentException().isThrownBy(() -> SavedPlace.create(memberId, PlaceProvider.KAKAO, "1",
				"이름", "주소", null, null, null, 37.0, 127.0, null));
	}

	@Test
	@DisplayName("제공자와 필수 문자열이 없으면 거절한다")
	void rejectsMissingRequiredValues() {
		assertThatIllegalArgumentException().isThrownBy(() -> SavedPlace.create(1L, null, "1", "이름", "주소",
				null, null, null, 37.0, 127.0, null));
		assertThatIllegalArgumentException().isThrownBy(() -> SavedPlace.create(1L, PlaceProvider.KAKAO, " ", "이름",
				"주소", null, null, null, 37.0, 127.0, null));
		assertThatIllegalArgumentException().isThrownBy(() -> SavedPlace.create(1L, PlaceProvider.KAKAO, "1", null,
				"주소", null, null, null, 37.0, 127.0, null));
		assertThatIllegalArgumentException().isThrownBy(() -> SavedPlace.create(1L, PlaceProvider.KAKAO, "1", "이름",
				"", null, null, null, 37.0, 127.0, null));
	}

	@Test
	@DisplayName("길이 한도를 넘으면 거절한다")
	void rejectsTooLongValues() {
		assertThatIllegalArgumentException().isThrownBy(() -> SavedPlace.create(1L, PlaceProvider.KAKAO,
				"1".repeat(65), "이름", "주소", null, null, null, 37.0, 127.0, null));
		assertThatIllegalArgumentException().isThrownBy(() -> SavedPlace.create(1L, PlaceProvider.KAKAO, "1",
				"가".repeat(201), "주소", null, null, null, 37.0, 127.0, null));
		assertThatIllegalArgumentException().isThrownBy(() -> SavedPlace.create(1L, PlaceProvider.KAKAO, "1",
				"이름", "주소", null, null, "C".repeat(11), 37.0, 127.0, null));
	}

	@Test
	@DisplayName("위도·경도가 범위를 벗어나거나 숫자가 아니면 거절한다")
	void rejectsInvalidCoordinates() {
		assertThatIllegalArgumentException().isThrownBy(() -> SavedPlace.create(1L, PlaceProvider.KAKAO, "1", "이름",
				"주소", null, null, null, 91.0, 127.0, null));
		assertThatIllegalArgumentException().isThrownBy(() -> SavedPlace.create(1L, PlaceProvider.KAKAO, "1", "이름",
				"주소", null, null, null, 37.0, -180.1, null));
		assertThatIllegalArgumentException().isThrownBy(() -> SavedPlace.create(1L, PlaceProvider.KAKAO, "1", "이름",
				"주소", null, null, null, Double.NaN, 127.0, null));
	}
}
