package com.hsu.oneclickmeet.domain.course.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hsu.oneclickmeet.domain.course.validation.CourseRequestScalarDeserializers;
import com.hsu.oneclickmeet.domain.course.validation.DistinctCoursePlaces;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CodePointLength;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@DistinctCoursePlaces
public record CreateCourseRequest(
	@JsonDeserialize(using = CourseRequestScalarDeserializers.StringValue.class)
	@NotBlank @CodePointLength(max = 100) String courseName,
	@NotNull @Size(min = 3, max = 5) List<@NotNull @Valid PlaceRequest> places
) {

	public CreateCourseRequest {
		courseName = stripName(courseName);
		places = copyList(places);
	}

	public record PlaceRequest(
		@JsonDeserialize(using = CourseRequestScalarDeserializers.LongValue.class)
		@NotNull @Positive Long placeId,
		@NotNull @Size(max = 50) List<@NotNull @Valid MenuRequest> menuList
	) {

		public PlaceRequest {
			menuList = copyList(menuList);
		}
	}

	public record MenuRequest(
		@JsonDeserialize(using = CourseRequestScalarDeserializers.LongValue.class)
		@JsonProperty(value = "menuId", required = true) @Null Long menuId,
		@JsonDeserialize(using = CourseRequestScalarDeserializers.StringValue.class)
		@NotBlank @CodePointLength(max = 100) String menuName,
		@JsonDeserialize(using = CourseRequestScalarDeserializers.IntegerValue.class)
		@NotNull @Min(0) @Max(1_000_000_000) Integer price,
		@JsonDeserialize(using = CourseRequestScalarDeserializers.BooleanValue.class)
		@NotNull Boolean selected
	) {

		public MenuRequest {
			menuName = stripName(menuName);
		}
	}

	private static <T> List<T> copyList(List<T> values) {
		// Preserve null elements so Bean Validation can report their indices.
		return values == null ? null : Collections.unmodifiableList(new ArrayList<>(values));
	}

	private static String stripName(String name) {
		if (name == null) {
			return null;
		}
		int start = 0;
		int end = name.length();
		while (start < end && isWhitespace(name.charAt(start))) {
			start++;
		}
		while (start < end && isWhitespace(name.charAt(end - 1))) {
			end--;
		}
		return name.substring(start, end);
	}

	private static boolean isWhitespace(char value) {
		return Character.isWhitespace(value) || Character.isSpaceChar(value) || value == '\u0085';
	}
}
