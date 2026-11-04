package com.hsu.oneclickmeet.domain.course.service;

import com.hsu.oneclickmeet.domain.course.dto.response.CourseDetailResponse.Place;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseDetailResponse.Warning;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseWalkingWarningTests {

	@Test
	void warnsOnlyForKnownSegmentsExceedingTenMinutes() {
		var places = List.of(place(1, null), place(2, null), place(3, 10), place(4, 11));

		assertThat(CourseReadService.walkingWarnings(places))
				.containsExactly(new Warning("WALKING_TIME_EXCEEDED", 4L, 11));
	}

	private Place place(long id, Integer minutes) {
		return new Place(id, id + 100, "장소 " + id, null, null, minutes, List.of(), 0);
	}
}
