package com.hsu.oneclickmeet.domain.course.dto.response;

import java.time.Instant;
import java.util.List;

public record CourseListResponse(List<Item> items, String nextCursor, boolean hasNext) {

	public record Item(long courseId, String courseName, long placeCount, Long originSharedCourseId, Instant createdAt) {
	}
}
