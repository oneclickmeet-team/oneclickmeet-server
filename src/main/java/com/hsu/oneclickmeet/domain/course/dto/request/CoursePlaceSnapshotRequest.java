package com.hsu.oneclickmeet.domain.course.dto.request;

import com.hsu.oneclickmeet.domain.course.validation.CourseRequestScalarDeserializers;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.CodePointLength;
import tools.jackson.databind.annotation.JsonDeserialize;

import static com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest.stripName;

public record CoursePlaceSnapshotRequest(
	@JsonDeserialize(using = CourseRequestScalarDeserializers.StringValue.class)
	@Pattern(regexp = "(?U)\\S{1,64}") String providerPlaceId,
	@JsonDeserialize(using = CourseRequestScalarDeserializers.StringValue.class)
	@NotBlank @CodePointLength(max = 200) String name,
	@JsonDeserialize(using = CourseRequestScalarDeserializers.StringValue.class)
	@CodePointLength(max = 300) String address,
	@JsonDeserialize(using = CourseRequestScalarDeserializers.DoubleValue.class)
	@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
	@JsonDeserialize(using = CourseRequestScalarDeserializers.DoubleValue.class)
	@NotNull @DecimalMin("-180") @DecimalMax("180") Double lng
) {
	public CoursePlaceSnapshotRequest {
		providerPlaceId = stripName(providerPlaceId);
		name = stripName(name);
		address = stripName(address);
		if (address != null && address.isEmpty()) {
			address = null;
		}
	}
}
