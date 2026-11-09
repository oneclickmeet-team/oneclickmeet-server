package com.hsu.oneclickmeet.domain.place.service;

import com.hsu.oneclickmeet.domain.place.client.PlaceCandidate;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchClient;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchCommand;
import com.hsu.oneclickmeet.domain.place.dto.response.NearbyPlaceResponse;
import com.hsu.oneclickmeet.domain.place.dto.response.NearbyPlacesResponse;
import com.hsu.oneclickmeet.domain.place.dto.response.PlaceCandidateResponse;
import com.hsu.oneclickmeet.domain.place.entity.SavedPlace;
import com.hsu.oneclickmeet.domain.place.exception.PlaceErrorCode;
import com.hsu.oneclickmeet.domain.place.repository.SavedPlaceRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;

/**
 * 보관함 장소 주변 놀거리. 저장된 장소의 좌표를 기준으로 카카오에서 종류별 검색어로 찾고,
 * 반경 1km 안의 장소만 가까운 순으로 돌려준다. 기준 장소 자신은 뺀다.
 * 사용자의 현재 위치가 아니라 저장된 장소의 좌표를 쓴다. 본인의 보관함 장소가 아니면 404다.
 */
@Service
public class NearbyPlaceService {

	static final double RADIUS_METERS = 1000.0;
	static final int SEARCH_SIZE = 15;
	private static final double EARTH_RADIUS_METERS = 6_371_000.0;

	private final SavedPlaceRepository repository;
	private final PlaceSearchClient searchClient;
	private final TransactionTemplate transaction;

	public NearbyPlaceService(SavedPlaceRepository repository, PlaceSearchClient searchClient,
			PlatformTransactionManager transactionManager) {
		this.repository = repository;
		this.searchClient = searchClient;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	public NearbyPlacesResponse nearby(long memberId, long savedPlaceId, NearbyCategory category) {
		SavedPlace base = transaction.execute(status -> repository.findByIdAndMemberId(savedPlaceId, memberId)
				.orElseThrow(() -> new BusinessException(PlaceErrorCode.NOT_FOUND)));
		List<PlaceCandidate> found = searchClient.search(new PlaceSearchCommand(category.keyword(), 1, SEARCH_SIZE,
				base.getLat(), base.getLng())).candidates();
		List<NearbyPlaceResponse> items = found.stream()
				.filter(candidate -> !candidate.providerPlaceId().equals(base.getProviderPlaceId()))
				.map(candidate -> new NearbyPlaceResponse(PlaceCandidateResponse.from(candidate),
						(int) Math.round(meters(base.getLat(), base.getLng(), candidate.lat(), candidate.lng()))))
				.filter(item -> item.distanceMeters() <= RADIUS_METERS)
				.sorted(Comparator.comparingInt(NearbyPlaceResponse::distanceMeters)
						.thenComparing(item -> item.place().providerPlaceId()))
				.toList();
		return new NearbyPlacesResponse(savedPlaceId, category, items);
	}

	static double meters(double lat1, double lng1, double lat2, double lng2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(a)));
	}
}
