package com.hsu.oneclickmeet.domain.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoursePlaceSnapshot {

	@Column(name = "provider", length = 16)
	private String provider;

	@Column(name = "provider_place_id", length = 64)
	private String providerPlaceId;

	@Column(name = "address", length = 300)
	private String address;

	@Column(name = "lat")
	private Double latitude;

	@Column(name = "lng")
	private Double longitude;

	public CoursePlaceSnapshot(String provider, String providerPlaceId, String address, Double latitude, Double longitude) {
		this.provider = provider;
		this.providerPlaceId = providerPlaceId;
		this.address = address;
		this.latitude = latitude;
		this.longitude = longitude;
	}
}
