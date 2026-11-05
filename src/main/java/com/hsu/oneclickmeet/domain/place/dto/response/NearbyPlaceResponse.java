package com.hsu.oneclickmeet.domain.place.dto.response;

/** 주변 장소 1건과 기준 장소로부터의 직선거리(미터, 반올림). */
public record NearbyPlaceResponse(PlaceCandidateResponse place, int distanceMeters) {
}
