package com.hsu.oneclickmeet.domain.place.service;

import com.hsu.oneclickmeet.domain.place.client.PlaceSearchClient;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchCommand;
import com.hsu.oneclickmeet.domain.place.client.PlaceSearchResult;
import com.hsu.oneclickmeet.domain.place.dto.request.PlaceSearchRequest;
import com.hsu.oneclickmeet.domain.place.dto.response.PlaceCandidateResponse;
import com.hsu.oneclickmeet.domain.place.dto.response.PlaceSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 장소 검색(후보 조회). DB를 읽거나 쓰지 않는다.
 * 카카오는 결과 상한을 넘는 page를 요청하면 오류나 빈 목록이 아니라 마지막 쪽을 다시 돌려주므로,
 * 상한을 넘는 요청은 카카오를 호출하지 않고 빈 결과로 응답한다.
 */
@Service
@RequiredArgsConstructor
public class PlaceSearchService {

	/** 카카오 키워드 검색에서 받을 수 있는 최대 결과 수(2026-10-30 실제 호출로 확인). */
	static final int MAX_RESULTS = 45;

	private final PlaceSearchClient placeSearchClient;

	public PlaceSearchResponse search(PlaceSearchRequest request) {
		int page = request.page();
		int size = request.size();
		if (isBeyondLimit(page, size)) {
			return PlaceSearchResponse.empty(page);
		}
		PlaceSearchResult result = placeSearchClient.search(
				new PlaceSearchCommand(request.query().trim(), page, size, null, null));
		boolean hasNext = result.hasNext() && (long) page * size < MAX_RESULTS;
		return new PlaceSearchResponse(
				result.candidates().stream().map(PlaceCandidateResponse::from).toList(),
				page,
				hasNext);
	}

	/** 요청한 쪽의 첫 번째 항목 위치가 결과 상한 이상이면 받을 수 있는 결과가 없다. */
	private boolean isBeyondLimit(int page, int size) {
		return (long) (page - 1) * size >= MAX_RESULTS;
	}
}
