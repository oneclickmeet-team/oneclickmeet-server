package com.hsu.oneclickmeet.domain.course.dto.response;

import java.time.Instant;

public record CourseUpdateResponse(long courseId, Instant updatedAt) {
}
