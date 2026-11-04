package com.hsu.oneclickmeet.domain.course.service;

import com.hsu.oneclickmeet.domain.couple.entity.CoupleStatus;
import com.hsu.oneclickmeet.domain.couple.exception.CoupleErrorCode;
import com.hsu.oneclickmeet.domain.couple.repository.CoupleRepository;
import com.hsu.oneclickmeet.domain.course.dto.request.UpdateSharedCourseRequest;
import com.hsu.oneclickmeet.domain.course.dto.response.SharedCourseUpdateResponse;
import com.hsu.oneclickmeet.domain.course.entity.Course;
import com.hsu.oneclickmeet.domain.course.entity.CoursePlace;
import com.hsu.oneclickmeet.domain.course.entity.CoursePlaceMenu;
import com.hsu.oneclickmeet.domain.course.entity.SharedCourse;
import com.hsu.oneclickmeet.domain.course.entity.SharedCourseUpdateReplay;
import com.hsu.oneclickmeet.domain.course.exception.CourseErrorCode;
import com.hsu.oneclickmeet.domain.course.repository.CourseRepository;
import com.hsu.oneclickmeet.domain.course.repository.CourseRequestLock;
import com.hsu.oneclickmeet.domain.course.repository.SharedCourseRepository;
import com.hsu.oneclickmeet.domain.course.repository.SharedCourseUpdateReplayRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class SharedCourseUpdateService {

	private static final Duration REPLAY_LIFETIME = Duration.ofHours(24);

	private final SharedCourseRepository sharedCourses;
	private final CourseRepository courses;
	private final SharedCourseUpdateReplayRepository replays;
	private final CourseRequestLock requestLock;
	private final CoupleRepository couples;
	private final Optional<CoursePlaceLookup> placeLookup;
	private final Clock clock;

	public SharedCourseUpdateService(SharedCourseRepository sharedCourses, CourseRepository courses,
			SharedCourseUpdateReplayRepository replays, CourseRequestLock requestLock, CoupleRepository couples,
			Optional<CoursePlaceLookup> placeLookup, @Qualifier("coupleClock") Clock clock) {
		this.sharedCourses = sharedCourses;
		this.courses = courses;
		this.replays = replays;
		this.requestLock = requestLock;
		this.couples = couples;
		this.placeLookup = placeLookup;
		this.clock = clock;
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public SharedCourseUpdateResponse update(long memberId, long sharedCourseId, UUID key,
			UpdateSharedCourseRequest request) {
		boolean acquired = requestLock.tryAcquireSharedUpdate(memberId, sharedCourseId, key);
		SharedCourse shared = requireEditableCourse(memberId, sharedCourseId, acquired);
		if (!acquired) {
			throw new BusinessException(CourseErrorCode.SAVE_IN_PROGRESS);
		}

		String hash = CourseRequestFingerprint.hash(request);
		var previous = replays.findByMemberIdAndSharedCourseIdAndRequestKey(memberId, sharedCourseId, key);
		if (previous.isPresent() && previous.get().isReplayable(now())) {
			var replay = previous.get();
			if (!replay.getRequestHash().equals(hash)) {
				throw new BusinessException(CourseErrorCode.IDEMPOTENCY_KEY_CONFLICT);
			}
			return new SharedCourseUpdateResponse(sharedCourseId, replay.getStructureVersion(), replay.getUpdatedAt());
		}

		boolean structureChanged = applyChanges(shared, memberId, request);
		Instant updatedAt = now();
		shared.recordEdit(memberId, updatedAt, structureChanged);
		sharedCourses.flush();

		var replay = previous.orElseGet(() -> new SharedCourseUpdateReplay(memberId, sharedCourseId, key));
		replay.complete(hash, shared.getStructureVersion(), updatedAt, now().plus(REPLAY_LIFETIME));
		replays.save(replay);
		return new SharedCourseUpdateResponse(sharedCourseId, shared.getStructureVersion(), updatedAt);
	}

	private SharedCourse requireEditableCourse(long memberId, long sharedCourseId, boolean lock) {
		// Serialize edits and relationship transitions before reading the shared snapshot.
		var current = lock ? couples.findCurrentForUpdate(memberId, now()) : couples.findCurrent(memberId, now());
		var couple = current.orElseThrow(() -> new BusinessException(CourseErrorCode.NOT_FOUND));
		var shared = sharedCourses.findByIdAndCoupleId(sharedCourseId, couple.getId())
				.orElseThrow(() -> new BusinessException(CourseErrorCode.NOT_FOUND));
		if (couple.getStatus() == CoupleStatus.GRACE) {
			throw new BusinessException(CoupleErrorCode.GRACE_READ_ONLY);
		}
		return shared;
	}

	private boolean applyChanges(SharedCourse shared, long memberId, UpdateSharedCourseRequest request) {
		boolean structural = request.placeOrder() != null || !request.placeAdds().isEmpty() || !request.placeDeletes().isEmpty();
		if (structural) {
			if (request.baseStructureVersion() == null || request.placeOrder() == null) {
				throw badRequest();
			}
			if (request.baseStructureVersion() != shared.getStructureVersion()) {
				throw structureConflict();
			}
		} else if (request.courseName() == null && request.menuUpserts().isEmpty() && request.menuDeletes().isEmpty()) {
			throw badRequest();
		}

		Course course = shared.getCourse();
		Map<Long, CoursePlace> places = new HashMap<>();
		Map<Long, CoursePlaceMenu> menus = new HashMap<>();
		for (CoursePlace place : course.getPlaces()) {
			places.put(place.getId(), place);
			for (CoursePlaceMenu menu : place.getMenuList()) {
				menus.put(menu.getId(), menu);
			}
		}

		Set<Long> deletedPlaces = distinctIds(request.placeDeletes());
		Map<UUID, CoursePlace> newPlaces = structural
				? applyStructure(course, memberId, request, places, deletedPlaces) : Map.of();
		applyMenus(memberId, request, places, menus, deletedPlaces, newPlaces);
		if (request.courseName() != null) {
			course.changeName(request.courseName());
		}
		return structural;
	}

	private Map<UUID, CoursePlace> applyStructure(Course course, long memberId, UpdateSharedCourseRequest request,
			Map<Long, CoursePlace> places, Set<Long> deletedPlaces) {
		deletedPlaces.forEach(id -> requirePlace(places, id));
		Set<Long> unorderedPlaces = new HashSet<>(places.keySet());
		unorderedPlaces.removeAll(deletedPlaces);
		Set<Long> originalPlaceIds = new HashSet<>();
		for (Long id : unorderedPlaces) {
			requireDistinctPlace(originalPlaceIds, places.get(id).getOriginalPlaceId());
		}
		Map<UUID, Long> placeAdds = new HashMap<>();
		for (var add : request.placeAdds()) {
			requireDistinctPlace(originalPlaceIds, add.placeId());
			if (placeAdds.putIfAbsent(add.clientPlaceKey(), add.placeId()) != null) {
				throw badRequest();
			}
		}
		Set<UUID> unorderedAdds = new HashSet<>(placeAdds.keySet());
		for (var order : request.placeOrder()) {
			if (order.coursePlaceId() != null) {
				requirePlace(places, order.coursePlaceId());
				if (!unorderedPlaces.remove(order.coursePlaceId())) {
					throw badRequest();
				}
			} else if (!unorderedAdds.remove(order.clientPlaceKey())) {
				throw badRequest();
			}
		}
		if (!unorderedPlaces.isEmpty() || !unorderedAdds.isEmpty()) {
			throw badRequest();
		}

		deletedPlaces.forEach(id -> course.removePlace(places.get(id)));
		Map<UUID, CoursePlace> newPlaces = new HashMap<>();
		int position = 1;
		for (var order : request.placeOrder()) {
			if (order.coursePlaceId() != null) {
				places.get(order.coursePlaceId()).changePosition(position++);
			} else {
				UUID key = order.clientPlaceKey();
				long placeId = placeAdds.get(key);
				String name = placeLookup.flatMap(lookup -> lookup.findPlaceName(memberId, placeId))
						.orElseThrow(() -> new BusinessException(CourseErrorCode.SOURCE_PLACE_NOT_FOUND));
				newPlaces.put(key, course.addPlace(placeId, name, position++));
			}
		}
		return newPlaces;
	}

	private void applyMenus(long memberId, UpdateSharedCourseRequest request, Map<Long, CoursePlace> places,
			Map<Long, CoursePlaceMenu> menus, Set<Long> deletedPlaces, Map<UUID, CoursePlace> newPlaces) {
		Set<Long> changedMenus = distinctIds(request.menuDeletes());
		for (Long id : changedMenus) {
			CoursePlaceMenu menu = requireMenu(menus, id);
			if (!deletedPlaces.contains(menu.getPlace().getId())) {
				menu.getPlace().removeMenu(menu);
			}
		}
		for (var upsert : request.menuUpserts()) {
			CoursePlace place;
			if (upsert.coursePlaceId() != null) {
				if (deletedPlaces.contains(upsert.coursePlaceId())) {
					throw badRequest();
				}
				place = requirePlace(places, upsert.coursePlaceId());
			} else {
				place = newPlaces.get(upsert.clientPlaceKey());
				if (place == null) {
					throw badRequest();
				}
			}
			if (upsert.menuId() == null) {
				if (place.getMenuList().size() >= 50) {
					throw badRequest();
				}
				place.appendMenu(upsert.menuName(), upsert.price(), upsert.selected(), memberId);
			} else {
				if (!changedMenus.add(upsert.menuId())) {
					throw badRequest();
				}
				CoursePlaceMenu menu = requireMenu(menus, upsert.menuId());
				if (!menu.getPlace().getId().equals(place.getId())) {
					throw badRequest();
				}
				menu.edit(upsert.menuName(), upsert.price(), upsert.selected(), memberId);
			}
		}
	}

	private CoursePlace requirePlace(Map<Long, CoursePlace> places, long id) {
		CoursePlace place = places.get(id);
		if (place == null) {
			throw courses.existsPlaceById(id) ? badRequest() : structureConflict();
		}
		return place;
	}

	private CoursePlaceMenu requireMenu(Map<Long, CoursePlaceMenu> menus, long id) {
		CoursePlaceMenu menu = menus.get(id);
		if (menu == null) {
			throw courses.existsMenuById(id) ? badRequest() : structureConflict();
		}
		return menu;
	}

	private static Set<Long> distinctIds(List<Long> ids) {
		Set<Long> distinct = new HashSet<>(ids);
		if (ids.size() != distinct.size()) {
			throw badRequest();
		}
		return distinct;
	}

	private static void requireDistinctPlace(Set<Long> originalPlaceIds, Long placeId) {
		if (placeId != null && !originalPlaceIds.add(placeId)) {
			throw badRequest();
		}
	}

	private static BusinessException badRequest() {
		return new BusinessException(CommonErrorCode.BAD_REQUEST);
	}

	private static BusinessException structureConflict() {
		return new BusinessException(CourseErrorCode.STRUCTURE_CONFLICT);
	}

	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}
}
