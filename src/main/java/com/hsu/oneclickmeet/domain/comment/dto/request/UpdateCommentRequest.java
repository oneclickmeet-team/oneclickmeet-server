package com.hsu.oneclickmeet.domain.comment.dto.request;

import com.hsu.oneclickmeet.domain.comment.validation.CommentContent;
import com.hsu.oneclickmeet.domain.course.validation.CourseRequestScalarDeserializers;
import tools.jackson.databind.annotation.JsonDeserialize;

public record UpdateCommentRequest(
		@JsonDeserialize(using = CourseRequestScalarDeserializers.StringValue.class)
		@CommentContent String content) {
}
