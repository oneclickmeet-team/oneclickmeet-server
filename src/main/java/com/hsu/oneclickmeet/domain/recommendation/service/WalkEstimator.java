package com.hsu.oneclickmeet.domain.recommendation.service;

/** 도보 시간 추정: 직선거리 × 우회계수 ÷ 분당 이동거리. 실제 보행 경로가 아니라 어림값이다. */
final class WalkEstimator {

	static final double DETOUR_FACTOR = 1.3;
	static final double METERS_PER_MINUTE = 80.0;
	private static final double EARTH_RADIUS_METERS = 6_371_000.0;

	private WalkEstimator() {
	}

	static double meters(double lat1, double lng1, double lat2, double lng2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(a)));
	}

	static int walkMinutes(double meters) {
		return (int) Math.ceil(meters * DETOUR_FACTOR / METERS_PER_MINUTE);
	}

	static int walkMinutes(CourseSpot from, CourseSpot to) {
		return walkMinutes(meters(from.lat(), from.lng(), to.lat(), to.lng()));
	}
}
