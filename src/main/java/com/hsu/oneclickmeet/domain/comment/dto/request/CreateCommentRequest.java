package com.hsu.oneclickmeet.domain.comment.dto.request;

import com.hsu.oneclickmeet.domain.comment.validation.CommentContent;
import com.hsu.oneclickmeet.domain.course.validation.CourseRequestScalarDeserializers;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateCommentRequest(
		@JsonDeserialize(using = CourseRequestScalarDeserializers.StringValue.class)
		@NotNull @Pattern(regexp = "SHARED_COURSE", message = "targetType은 SHARED_COURSE여야 합니다.") String targetType,
		@JsonDeserialize(using = CourseRequestScalarDeserializers.LongValue.class)
		@NotNull @Positive Long targetId,
		@JsonDeserialize(using = CourseRequestScalarDeserializers.StringValue.class)
		@CommentContent String content) {
}
