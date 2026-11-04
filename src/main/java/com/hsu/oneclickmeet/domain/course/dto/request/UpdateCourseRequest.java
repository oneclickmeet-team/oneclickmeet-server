package com.hsu.oneclickmeet.domain.course.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hsu.oneclickmeet.domain.course.validation.CourseRequestScalarDeserializers;
import com.hsu.oneclickmeet.domain.course.validation.DistinctCourseUpdateReferences;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CodePointLength;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

import static com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.copyList;
import static com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.stripName;

@DistinctCourseUpdateReferences
public record UpdateCourseRequest(
	@JsonDeserialize(using = CourseRequestScalarDeserializers.StringValue.class)
	@NotBlank @CodePointLength(max = 100) String courseName,
	@NotNull @Size(min = 3, max = 5) List<@NotNull @Valid PlaceRequest> places
) {

	public UpdateCourseRequest {
		courseName = stripName(courseName);
		places = copyList(places);
	}

	public record PlaceRequest(
		@JsonDeserialize(using = CourseRequestScalarDeserializers.LongValue.class)
		@JsonProperty(value = "coursePlaceId", required = true) @Positive Long coursePlaceId,
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
		@JsonProperty(value = "menuId", required = true) @Positive Long menuId,
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
}
