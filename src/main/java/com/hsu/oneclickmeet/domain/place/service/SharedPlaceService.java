package com.hsu.oneclickmeet.domain.place.service;

import com.hsu.oneclickmeet.domain.comment.entity.CommentTargetType;
import com.hsu.oneclickmeet.domain.comment.repository.CommentCreateRequestRepository;
import com.hsu.oneclickmeet.domain.comment.repository.CommentRepository;
import com.hsu.oneclickmeet.domain.couple.dto.response.PartnerResponse;
import com.hsu.oneclickmeet.domain.couple.entity.Couple;
import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;
import com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.member.repository.MemberRepository;
import com.hsu.oneclickmeet.domain.memo.entity.MemoTargetType;
import com.hsu.oneclickmeet.domain.memo.repository.MemoRepository;
import com.hsu.oneclickmeet.domain.memo.repository.MemoCreateReplayRepository;
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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class SharedPlaceService {

	private final SavedPlaceRepository savedPlaces;
	private final SharedPlaceRepository sharedPlaces;
	private final CoupleRepository couples;
	private final MemberRepository members;
	private final Clock clock;
	private final MemoRepository memos;
	private final MemoCreateReplayRepository memoReplays;
	private final CommentRepository comments;
	private final CommentCreateRequestRepository commentReplays;

	public SharedPlaceService(SavedPlaceRepository savedPlaces, SharedPlaceRepository sharedPlaces,
			CoupleRepository couples, MemberRepository members, MemoRepository memos, MemoCreateReplayRepository memoReplays,
			CommentRepository comments, CommentCreateRequestRepository commentReplays, @Qualifier("coupleClock") Clock clock) {
		this.savedPlaces = savedPlaces;
		this.sharedPlaces = sharedPlaces;
		this.couples = couples;
		this.members = members;
		this.clock = clock;
		this.memos = memos;
		this.memoReplays = memoReplays;
		this.comments = comments;
		this.commentReplays = commentReplays;
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
			return new ShareResult(response(existing.get()), false);
		}
		var created = sharedPlaces.save(SharedPlace.copyFrom(couple, source, memberId, sharedAt));
		return new ShareResult(response(created), true);
	}

	@Transactional(readOnly = true)
	public SharedPlaceListResponse list(long memberId, String cursor, int size) {
		Couple couple = couples.findCurrent(memberId, now())
				.orElseThrow(() -> new BusinessException(CoupleErrorCode.STATE_CONFLICT));
		long coupleId = couple.getId();
		Long beforeId = SharedPlaceCursor.decode(cursor, coupleId);
		var found = sharedPlaces.findPage(coupleId, beforeId, PageRequest.ofSize(size + 1));
		boolean hasNext = found.size() > size;
		var page = found.stream().limit(size).toList();
		var ids = page.stream().map(SharedPlace::getSharedByMemberId).filter(Objects::nonNull).distinct().toList();
		var names = members.findAllById(ids).stream().collect(Collectors.toMap(
				member -> member.getId(), member -> member.getNickname()));
		var items = page.stream().map(place -> SharedPlaceResponse.from(place, sharer(place, names))).toList();
		String nextCursor = hasNext ? SharedPlaceCursor.encode(coupleId, items.getLast().sharedPlaceId()) : null;
		boolean readOnly = couple.getStatus() == CoupleStatus.GRACE;
		return new SharedPlaceListResponse(coupleId, couple.getStatus(), readOnly, couple.getGraceEndsAt(),
				items, nextCursor, hasNext);
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void remove(long memberId, long sharedPlaceId) {
		Couple couple = couples.findCurrentForUpdate(memberId, now())
				.orElseThrow(() -> new BusinessException(PlaceErrorCode.NOT_FOUND));
		if (couple.getStatus() != CoupleStatus.CONNECTED) {
			throw new BusinessException(couple.isInGrace(now())
					? CoupleErrorCode.GRACE_READ_ONLY : PlaceErrorCode.NOT_FOUND);
		}
		var place = sharedPlaces.findByIdAndCouple_Id(sharedPlaceId, couple.getId())
				.orElseThrow(() -> new BusinessException(PlaceErrorCode.NOT_FOUND));
		commentReplays.deleteForSharedPlace(sharedPlaceId);
		comments.deleteForTarget(CommentTargetType.SHARED_PLACE, sharedPlaceId);
		memoReplays.deleteForTarget(MemoTargetType.SHARED_PLACE, sharedPlaceId);
		memos.deleteForTarget(MemoTargetType.SHARED_PLACE, sharedPlaceId);
		sharedPlaces.delete(place);
	}

	private SharedPlaceResponse response(SharedPlace place) {
		Long id = place.getSharedByMemberId();
		PartnerResponse sharedBy = id == null ? null : new PartnerResponse(id,
				members.findById(id).map(member -> member.getNickname()).orElse(null));
		return SharedPlaceResponse.from(place, sharedBy);
	}

	private static PartnerResponse sharer(SharedPlace place, Map<Long, String> names) {
		Long id = place.getSharedByMemberId();
		return id == null ? null : new PartnerResponse(id, names.get(id));
	}

	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}

	public record ShareResult(SharedPlaceResponse place, boolean created) {
	}
}
