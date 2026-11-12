package com.hsu.oneclickmeet.domain.course.service;

import com.hsu.oneclickmeet.domain.course.dto.request.CoursePlaceSnapshotRequest;
import com.hsu.oneclickmeet.domain.course.entity.CoursePlaceSnapshot;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;

import java.util.HashSet;
import java.util.Set;

final class CoursePlaceSelection {
	private final Set<Long> savedPlaceIds = new HashSet<>();
	private final Set<String> providerPlaceIds = new HashSet<>();
	private final Set<NameAndCoordinates> placesWithoutProviderId = new HashSet<>();

	static CoursePlaceSnapshot snapshotOf(CoursePlaceSnapshotRequest request) {
		String provider = request.providerPlaceId() == null ? null : "KAKAO";
		return new CoursePlaceSnapshot(provider, request.providerPlaceId(), request.address(), request.lat(), request.lng());
	}

	void add(Long savedPlaceId, String name, CoursePlaceSnapshot snapshot) {
		boolean added = true;
		if (savedPlaceId != null) {
			added = savedPlaceIds.add(savedPlaceId);
		} else if (snapshot != null && snapshot.getProviderPlaceId() != null) {
			added = providerPlaceIds.add(snapshot.getProviderPlaceId());
		} else if (snapshot != null) {
			added = placesWithoutProviderId.add(new NameAndCoordinates(name, snapshot.getLatitude(), snapshot.getLongitude()));
		}
		if (!added) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST);
		}
	}

	private record NameAndCoordinates(String name, Double latitude, Double longitude) {
	}
}
