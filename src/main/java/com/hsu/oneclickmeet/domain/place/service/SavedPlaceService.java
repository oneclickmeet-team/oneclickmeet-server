package com.hsu.oneclickmeet.domain.place.service;

import com.hsu.oneclickmeet.domain.place.dto.request.SaveSavedPlaceRequest;
import com.hsu.oneclickmeet.domain.place.dto.response.SavedPlaceListResponse;
import com.hsu.oneclickmeet.domain.place.dto.response.SavedPlaceResponse;
import com.hsu.oneclickmeet.domain.place.entity.PlaceProvider;
import com.hsu.oneclickmeet.domain.place.entity.SavedPlace;
import com.hsu.oneclickmeet.domain.place.exception.PlaceErrorCode;
import com.hsu.oneclickmeet.domain.place.repository.SavedPlaceRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

/**
 * 개인 보관함. 모든 조회·삭제는 회원 본인의 장소만 대상으로 하며,
 * 존재하지 않거나 다른 회원의 장소는 구분하지 않고 같은 404로 응답한다.
 */
@Service
public class SavedPlaceService {

	private static final PlaceProvider PROVIDER = PlaceProvider.KAKAO;

	private final SavedPlaceRepository repository;
	private final TransactionTemplate transaction;

	public SavedPlaceService(SavedPlaceRepository repository, PlatformTransactionManager transactionManager) {
		this.repository = repository;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/** 같은 장소를 이미 저장했으면 새로 만들지 않고 기존 항목을 돌려준다(두 번 눌러도 안전). */
	public SaveResult save(long memberId, SaveSavedPlaceRequest request) {
		try {
			return transaction.execute(status -> createIfAbsent(memberId, request));
		} catch (DataIntegrityViolationException exception) {
			// 같은 장소를 동시에 저장한 요청이 먼저 커밋된 경우: 먼저 저장된 항목을 돌려준다.
			Optional<SaveResult> existing = transaction.execute(status -> repository
					.findByMemberIdAndProviderAndProviderPlaceId(memberId, PROVIDER, request.providerPlaceId().trim())
					.map(place -> new SaveResult(SavedPlaceResponse.from(place), false)));
			return existing.orElseThrow(() -> exception);
		}
	}

	public SavedPlaceListResponse list(long memberId, String cursor, int size, String categoryGroupCode) {
		String category = categoryGroupCode == null || categoryGroupCode.isBlank() ? null : categoryGroupCode.trim();
		SavedPlaceCursor position = SavedPlaceCursor.decode(cursor, memberId, category);
		List<SavedPlace> found = transaction.execute(status -> repository.findPage(
				memberId, category,
				position == null ? null : position.createdAt(),
				position == null ? null : position.id(),
				PageRequest.ofSize(size + 1)));
		boolean hasNext = found.size() > size;
		List<SavedPlace> page = hasNext ? found.subList(0, size) : found;
		String nextCursor = hasNext
				? new SavedPlaceCursor(page.get(page.size() - 1).getCreatedAt(), page.get(page.size() - 1).getId())
						.encode(memberId, category)
				: null;
		return new SavedPlaceListResponse(page.stream().map(SavedPlaceResponse::from).toList(), nextCursor, hasNext);
	}

	public SavedPlaceResponse detail(long memberId, long savedPlaceId) {
		return transaction.execute(status -> repository.findByIdAndMemberId(savedPlaceId, memberId)
				.map(SavedPlaceResponse::from)
				.orElseThrow(() -> new BusinessException(PlaceErrorCode.NOT_FOUND)));
	}

	public void delete(long memberId, long savedPlaceId) {
		transaction.executeWithoutResult(status -> repository.delete(
				repository.findByIdAndMemberId(savedPlaceId, memberId)
						.orElseThrow(() -> new BusinessException(PlaceErrorCode.NOT_FOUND))));
	}

	private SaveResult createIfAbsent(long memberId, SaveSavedPlaceRequest request) {
		String providerPlaceId = request.providerPlaceId().trim();
		Optional<SavedPlace> existing = repository
				.findByMemberIdAndProviderAndProviderPlaceId(memberId, PROVIDER, providerPlaceId);
		if (existing.isPresent()) {
			return new SaveResult(SavedPlaceResponse.from(existing.get()), false);
		}
		SavedPlace created = repository.saveAndFlush(SavedPlace.create(memberId, PROVIDER, providerPlaceId,
				request.name(), request.address(), request.roadAddress(), request.category(),
				request.categoryGroupCode(), request.lat(), request.lng(), request.placeUrl()));
		return new SaveResult(SavedPlaceResponse.from(created), true);
	}

	public record SaveResult(SavedPlaceResponse place, boolean created) {
	}
}
