package com.hsu.oneclickmeet.domain.course.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hsu.oneclickmeet.domain.course.validation.CourseRequestScalarDeserializers.BooleanValue;
import com.hsu.oneclickmeet.domain.course.validation.CourseRequestScalarDeserializers.IntegerValue;
import com.hsu.oneclickmeet.domain.course.validation.CourseRequestScalarDeserializers.LongValue;
import com.hsu.oneclickmeet.domain.course.validation.CourseRequestScalarDeserializers.StringValue;
import com.hsu.oneclickmeet.domain.course.validation.SharedCoursePatchJson.OptionalNonNullValue;
import com.hsu.oneclickmeet.domain.course.validation.SharedCoursePatchJson.StrictObject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CodePointLength;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.List;
import java.util.UUID;

import static com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.copyList;
import static com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.stripName;

public record UpdateSharedCourseRequest(
		@JsonDeserialize(using = OptionalNonNullValue.class)
		@Size(min = 1, message = "코스 이름을 입력해야 합니다.")
		@CodePointLength(max = 100) String courseName,
		@JsonDeserialize(using = OptionalNonNullValue.class)
		@PositiveOrZero Long baseStructureVersion,
		@JsonDeserialize(using = OptionalNonNullValue.class)
		List<@NotNull @Valid PlaceAdd> placeAdds,
		@JsonDeserialize(using = OptionalNonNullValue.class, contentUsing = LongValue.class)
		List<@NotNull @Positive Long> placeDeletes,
		@JsonDeserialize(using = OptionalNonNullValue.class)
		@Size(min = 3, max = 5) List<@NotNull @Valid PlaceOrder> placeOrder,
		@JsonDeserialize(using = OptionalNonNullValue.class)
		List<@NotNull @Valid MenuUpsert> menuUpserts,
		@JsonDeserialize(using = OptionalNonNullValue.class, contentUsing = LongValue.class)
		List<@NotNull @Positive Long> menuDeletes
) implements StrictObject {

	public UpdateSharedCourseRequest {
		courseName = stripName(courseName);
		placeAdds = listOrEmpty(placeAdds);
		placeDeletes = listOrEmpty(placeDeletes);
		placeOrder = copyList(placeOrder);
		menuUpserts = listOrEmpty(menuUpserts);
		menuDeletes = listOrEmpty(menuDeletes);
	}

	public record PlaceAdd(
			@JsonDeserialize(using = OptionalNonNullValue.class)
			@NotNull UUID clientPlaceKey,
			@JsonDeserialize(using = LongValue.class)
			@NotNull @Positive Long placeId
	) implements StrictObject {
	}

	public record PlaceOrder(
			@JsonDeserialize(using = OptionalNonNullValue.class)
			@Positive Long coursePlaceId,
			@JsonDeserialize(using = OptionalNonNullValue.class)
			UUID clientPlaceKey
	) implements StrictObject {

		@AssertTrue(message = "coursePlaceId와 clientPlaceKey 중 하나를 지정해야 합니다.")
		private boolean isPlaceReferenceValid() {
			return (coursePlaceId != null) != (clientPlaceKey != null);
		}
	}

	public record MenuUpsert(
			@JsonDeserialize(using = OptionalNonNullValue.class)
			@Positive Long coursePlaceId,
			@JsonDeserialize(using = OptionalNonNullValue.class)
			UUID clientPlaceKey,
			@JsonDeserialize(using = LongValue.class)
			@JsonProperty(value = "menuId", required = true) @Positive Long menuId,
			@JsonDeserialize(using = StringValue.class)
			@NotNull @Size(min = 1, message = "메뉴 이름을 입력해야 합니다.")
			@CodePointLength(max = 100) String menuName,
			@JsonDeserialize(using = IntegerValue.class)
			@NotNull @Min(0) @Max(1_000_000_000) Integer price,
			@JsonDeserialize(using = BooleanValue.class)
			@NotNull Boolean selected
	) implements StrictObject {

		public MenuUpsert {
			menuName = stripName(menuName);
		}

		@AssertTrue(message = "coursePlaceId와 clientPlaceKey 중 하나를 지정해야 합니다.")
		private boolean isPlaceReferenceValid() {
			return (coursePlaceId != null) != (clientPlaceKey != null);
		}
	}

	private static <T> List<T> listOrEmpty(List<T> values) {
		return values == null ? List.of() : copyList(values);
	}
}
