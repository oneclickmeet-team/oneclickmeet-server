package com.hsu.oneclickmeet.domain.place.service;

import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;
import com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.place.dto.response.SharedPlaceListResponse;
import com.hsu.oneclickmeet.domain.place.dto.response.SharedPlaceResponse;
import com.hsu.oneclickmeet.domain.place.entity.SharedPlace;
import com.hsu.oneclickmeet.domain.place.exception.PlaceErrorCode;
import com.hsu.oneclickmeet.domain.place.repository.SavedPlaceRepository;
import com.hsu.oneclickmeet.domain.place.repository.SharedPlaceRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class SharedPlaceService {

	private final SavedPlaceRepository savedPlaces;
	private final SharedPlaceRepository sharedPlaces;
	private final CoupleRepository couples;
	private final Clock clock;

	public SharedPlaceService(SavedPlaceRepository savedPlaces, SharedPlaceRepository sharedPlaces,
			CoupleRepository couples, @Qualifier("coupleClock") Clock clock) {
		this.savedPlaces = savedPlaces;
		this.sharedPlaces = sharedPlaces;
		this.couples = couples;
		this.clock = clock;
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public ShareResult share(long memberId, long savedPlaceId) {
		var source = savedPlaces.findByIdAndMemberId(savedPlaceId, memberId)
				.orElseThrow(() -> new BusinessException(PlaceErrorCode.NOT_FOUND));
		Couple couple = couples.findCurrentForUpdate(memberId, now())
				.orElseThrow(() -> new BusinessException(CoupleErrorCode.STATE_CONFLICT));
		Instant sharedAt = now();
		if (couple.getStatus() != CoupleStatus.CONNECTED) {
			throw new BusinessException(couple.isInGrace(sharedAt)
					? CoupleErrorCode.GRACE_READ_ONLY : CoupleErrorCode.STATE_CONFLICT);
		}

		// Hold the couple lock until commit so duplicate requests see the first snapshot.
		var existing = sharedPlaces.findByCouple_IdAndProviderAndProviderPlaceId(
				couple.getId(), source.getProvider(), source.getProviderPlaceId());
		if (existing.isPresent()) {
			return new ShareResult(SharedPlaceResponse.from(existing.get()), false);
		}
		var created = sharedPlaces.save(SharedPlace.copyFrom(couple, source, sharedAt));
		return new ShareResult(SharedPlaceResponse.from(created), true);
	}

	@Transactional(readOnly = true)
	public SharedPlaceListResponse list(long memberId, String cursor, int size) {
		Couple couple = couples.findCurrent(memberId, now())
				.orElseThrow(() -> new BusinessException(CoupleErrorCode.STATE_CONFLICT));
		long coupleId = couple.getId();
		Long beforeId = SharedPlaceCursor.decode(cursor, coupleId);
		var found = sharedPlaces.findPage(coupleId, beforeId, PageRequest.ofSize(size + 1));
		boolean hasNext = found.size() > size;
		var items = found.stream().limit(size).map(SharedPlaceResponse::from).toList();
		String nextCursor = hasNext ? SharedPlaceCursor.encode(coupleId, items.getLast().sharedPlaceId()) : null;
		boolean readOnly = couple.getStatus() == CoupleStatus.GRACE;
		return new SharedPlaceListResponse(coupleId, couple.getStatus(), readOnly, couple.getGraceEndsAt(),
				items, nextCursor, hasNext);
	}

	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}

	public record ShareResult(SharedPlaceResponse place, boolean created) {
	}
}
