package com.hsu.oneclickmeet.domain.place.entity;

import com.hsu.oneclickmeet.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 개인 보관함에 저장한 장소. 장소 정보(이름·주소·좌표 등)는 저장 시점의 값을 이 행에 함께 보관한다(A안).
 * 같은 장소를 여러 사용자가 저장하면 사용자별로 행이 생기며, 한 사용자는 같은 장소를 한 번만 저장할 수 있다.
 */
@Entity
@Getter
@Table(name = "saved_places",
		uniqueConstraints = @UniqueConstraint(name = SavedPlace.MEMBER_PLACE_UNIQUE,
				columnNames = {"member_id", "provider", "provider_place_id"}),
		indexes = @Index(name = "idx_saved_places_member_created", columnList = "member_id, created_at, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedPlace extends BaseEntity {

	public static final String MEMBER_PLACE_UNIQUE = "uk_saved_places_member_place";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private long memberId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private PlaceProvider provider;

	@Column(nullable = false, updatable = false, length = 64)
	private String providerPlaceId;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(nullable = false, length = 300)
	private String address;

	@Column(length = 300)
	private String roadAddress;

	@Column(length = 200)
	private String category;

	@Column(length = 10)
	private String categoryGroupCode;

	@Column(nullable = false)
	private double lat;

	@Column(nullable = false)
	private double lng;

	@Column(length = 500)
	private String placeUrl;

	private SavedPlace(long memberId, PlaceProvider provider, String providerPlaceId, String name, String address,
			String roadAddress, String category, String categoryGroupCode, double lat, double lng, String placeUrl) {
		this.memberId = memberId;
		this.provider = provider;
		this.providerPlaceId = providerPlaceId;
		this.name = name;
		this.address = address;
		this.roadAddress = roadAddress;
		this.category = category;
		this.categoryGroupCode = categoryGroupCode;
		this.lat = lat;
		this.lng = lng;
		this.placeUrl = placeUrl;
	}

	public static SavedPlace create(long memberId, PlaceProvider provider, String providerPlaceId, String name,
			String address, String roadAddress, String category, String categoryGroupCode, double lat, double lng,
			String placeUrl) {
		if (memberId <= 0) {
			throw new IllegalArgumentException("memberId는 양수여야 합니다.");
		}
		if (provider == null) {
			throw new IllegalArgumentException("provider는 필수입니다.");
		}
		requireText(providerPlaceId, 64, "providerPlaceId");
		requireText(name, 200, "name");
		requireText(address, 300, "address");
		limitText(roadAddress, 300, "roadAddress");
		limitText(category, 200, "category");
		limitText(categoryGroupCode, 10, "categoryGroupCode");
		limitText(placeUrl, 500, "placeUrl");
		if (!(lat >= -90.0 && lat <= 90.0)) {
			throw new IllegalArgumentException("lat는 -90 이상 90 이하여야 합니다.");
		}
		if (!(lng >= -180.0 && lng <= 180.0)) {
			throw new IllegalArgumentException("lng는 -180 이상 180 이하여야 합니다.");
		}
		return new SavedPlace(memberId, provider, providerPlaceId.trim(), name.trim(), address.trim(),
				blankToNull(roadAddress), blankToNull(category), blankToNull(categoryGroupCode), lat, lng,
				blankToNull(placeUrl));
	}

	private static void requireText(String value, int maxLength, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + "는 필수입니다.");
		}
		limitText(value, maxLength, field);
	}

	private static void limitText(String value, int maxLength, String field) {
		if (value != null && value.trim().length() > maxLength) {
			throw new IllegalArgumentException(field + "는 " + maxLength + "자 이하여야 합니다.");
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
