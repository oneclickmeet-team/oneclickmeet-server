package com.hsu.oneclickmeet.domain.course.service;

import com.hsu.oneclickmeet.domain.course.dto.request.UpdateCourseRequest;
import com.hsu.oneclickmeet.domain.course.dto.response.CourseUpdateResponse;
import com.hsu.oneclickmeet.domain.course.entity.Course;
import com.hsu.oneclickmeet.domain.course.entity.CoursePlace;
import com.hsu.oneclickmeet.domain.course.entity.CoursePlaceMenu;
import com.hsu.oneclickmeet.domain.course.entity.CourseUpdateRequestReceipt;
import com.hsu.oneclickmeet.domain.course.exception.CourseErrorCode;
import com.hsu.oneclickmeet.domain.course.repository.CourseLibraryRepository;
import com.hsu.oneclickmeet.domain.course.repository.CourseRepository;
import com.hsu.oneclickmeet.domain.course.repository.CourseRequestLock;
import com.hsu.oneclickmeet.domain.course.repository.CourseUpdateRequestRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import com.hsu.oneclickmeet.global.exception.CommonErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Validated
@RequiredArgsConstructor
public class CourseUpdateService {
	private final CourseRepository courseRepository;
	private final CourseLibraryRepository courseLibraryRepository;
	private final CourseRequestLock requestLock;
	private final CourseUpdateRequestRepository updateRequestRepository;
	private final Optional<CoursePlaceLookup> placeLookup;

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public CourseUpdateResponse update(@Positive long memberId, @Positive long courseId, @NotNull UUID requestKey,
			@Valid @NotNull UpdateCourseRequest request) {
		requireOwnership(memberId, courseId);
		requestLock.acquireUpdate(memberId, courseId, requestKey);
		Course course = courseRepository.findOwnedCourseForUpdate(memberId, courseId)
				.orElseThrow(() -> new BusinessException(CourseErrorCode.NOT_FOUND));
		// A concurrent delete may have removed the ownership link while we waited on the course row.
		requireOwnership(memberId, courseId);

		String requestHash = CourseRequestFingerprint.hash(request);
		var previous = updateRequestRepository.findByMemberIdAndCourseIdAndRequestKey(memberId, courseId, requestKey);
		if (previous.isPresent()) {
			CourseUpdateRequestReceipt receipt = previous.get();
			if (!receipt.getRequestHash().equals(requestHash)) {
				throw new BusinessException(CourseErrorCode.IDEMPOTENCY_KEY_CONFLICT);
			}
			return new CourseUpdateResponse(courseId, receipt.getUpdatedAt());
		}

		List<Course.PlaceChange> changes = request.places().stream()
				.map(place -> toPlaceChange(memberId, course, place))
				.toList();
		var selection = new CoursePlaceSelection();
		changes.forEach(change -> selection.add(change.originalPlaceId(), change.name(), change.snapshot()));
		course.replaceContents(request.courseName(), changes);
		// Hibernate's root auditing does not run when only children change.
		Instant updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
		courseRepository.recordUpdatedAt(courseId, updatedAt);
		updateRequestRepository.save(new CourseUpdateRequestReceipt(memberId, courseId, requestKey, requestHash, updatedAt));
		return new CourseUpdateResponse(courseId, updatedAt);
	}

	private void requireOwnership(long memberId, long courseId) {
		if (!courseLibraryRepository.existsByMemberIdAndCourse_Id(memberId, courseId)) {
			throw new BusinessException(CourseErrorCode.NOT_FOUND);
		}
	}

	private Course.PlaceChange toPlaceChange(long memberId, Course course, UpdateCourseRequest.PlaceRequest request) {
		var existing = request.coursePlaceId() == null ? null : findExistingPlace(course, request);
		var existingMenuIds = new HashSet<Long>();
		if (existing != null) {
			existing.getMenuList().stream().map(CoursePlaceMenu::getId).forEach(existingMenuIds::add);
		}
		List<Course.MenuChange> menus = request.menuList().stream().map(menu -> {
			if (menu.menuId() != null && !existingMenuIds.contains(menu.menuId())) {
				throw new BusinessException(CommonErrorCode.BAD_REQUEST);
			}
			return new Course.MenuChange(menu.menuId(), menu.menuName(), menu.price(), menu.selected());
		}).toList();
		if (existing != null) {
			return new Course.PlaceChange(existing.getId(), existing.getOriginalPlaceId(), existing.getName(), menus,
					existing.getSnapshot());
		}
		var snapshotRequest = request.placeSnapshot();
		if (snapshotRequest != null) {
			return new Course.PlaceChange(null, null, snapshotRequest.name(), menus,
					CoursePlaceSelection.snapshotOf(snapshotRequest));
		}
		String name = placeLookup.flatMap(lookup -> lookup.findPlaceName(memberId, request.placeId()))
				.orElseThrow(() -> new BusinessException(CourseErrorCode.SOURCE_PLACE_NOT_FOUND));
		return new Course.PlaceChange(null, request.placeId(), name, menus, null);
	}

	private CoursePlace findExistingPlace(Course course, UpdateCourseRequest.PlaceRequest request) {
		return course.getPlaces().stream()
				.filter(place -> request.coursePlaceId().equals(place.getId())
						&& (request.placeId() == null || Objects.equals(request.placeId(), place.getOriginalPlaceId())))
				.findFirst()
				.orElseThrow(() -> new BusinessException(CommonErrorCode.BAD_REQUEST));
	}
}
