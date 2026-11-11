package com.hsu.oneclickmeet.domain.course.service;

import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest;
import com.hsu.oneclickmeet.domain.course.dto.request.UpdateCourseRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseRequestFingerprintTests {
	// Fingerprints produced from the same requests by the pre-snapshot DTOs.

	@Test
	void 스냅샷이_없는_생성_요청의_기존_멱등성_해시를_유지한다() {
		var request = new CreateCourseRequest("Course", List.of(
				new CreateCourseRequest.PlaceRequest(1L, List.of(new CreateCourseRequest.MenuRequest(null, "Tea", 10, false))),
				new CreateCourseRequest.PlaceRequest(2L, List.of()),
				new CreateCourseRequest.PlaceRequest(3L, List.of())));

		assertThat(CourseRequestFingerprint.hash(request))
				.isEqualTo("1bf51bfc158ef42b1fd46db2f7056c59a5f53af8e9ea9f072f5911ec55a3b945");
	}

	@Test
	void 스냅샷이_없는_수정_요청의_기존_멱등성_해시를_유지한다() {
		var request = new UpdateCourseRequest("Course", List.of(
				new UpdateCourseRequest.PlaceRequest(11L, 1L, List.of(new UpdateCourseRequest.MenuRequest(null, "Tea", 10, false))),
				new UpdateCourseRequest.PlaceRequest(12L, 2L, List.of()),
				new UpdateCourseRequest.PlaceRequest(null, 3L, List.of())));

		assertThat(CourseRequestFingerprint.hash(request))
				.isEqualTo("b563f5e980a7516a388817dd957a7cac12656c59209780566da3e8f281058553");
	}
}
