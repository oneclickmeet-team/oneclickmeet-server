package com.hsu.oneclickmeet.domain.course.service;

import java.util.Optional;

@FunctionalInterface
public interface CoursePlaceLookup {
	/** Resolves the original place ID only when the member may use it. */
	Optional<String> findPlaceName(long memberId, long placeId);
}
