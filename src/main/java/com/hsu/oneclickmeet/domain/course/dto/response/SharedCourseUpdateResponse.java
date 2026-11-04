package com.hsu.oneclickmeet.domain.course.dto.response;

import java.time.Instant;

public record SharedCourseUpdateResponse(long sharedCourseId, long structureVersion, Instant updatedAt) {
}
