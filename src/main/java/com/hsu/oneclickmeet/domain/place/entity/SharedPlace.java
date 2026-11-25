package com.hsu.oneclickmeet.domain.place.entity;

import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Entity
@Getter
@Table(name = "shared_places",
		uniqueConstraints = @UniqueConstraint(name = "uk_shared_places_couple_place",
				columnNames = {"couple_id", "provider", "provider_place_id"}),
		indexes = @Index(name = "idx_shared_places_couple_id", columnList = "couple_id, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SharedPlace {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "couple_id", nullable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_shared_places_couple"))
	private Couple couple;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PlaceProvider provider;

	@Column(nullable = false, length = 64)
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

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "json")
	private List<SavedPlaceTag> tags;

	@Column(updatable = false)
	private Long sharedByMemberId;

	@Column(nullable = false, updatable = false)
	private Instant sharedAt;

	private SharedPlace(Couple couple, SavedPlace source, long memberId, Instant sharedAt) {
		this.couple = couple;
		this.sharedByMemberId = memberId;
		this.provider = source.getProvider();
		this.providerPlaceId = source.getProviderPlaceId();
		this.name = source.getName();
		this.address = source.getAddress();
		this.roadAddress = source.getRoadAddress();
		this.category = source.getCategory();
		this.categoryGroupCode = source.getCategoryGroupCode();
		this.lat = source.getLat();
		this.lng = source.getLng();
		this.placeUrl = source.getPlaceUrl();
		this.tags = List.copyOf(source.getTags());
		this.sharedAt = sharedAt;
	}

	public static SharedPlace copyFrom(Couple couple, SavedPlace source, long memberId, Instant sharedAt) {
		return new SharedPlace(couple, source, memberId, sharedAt);
	}
}
