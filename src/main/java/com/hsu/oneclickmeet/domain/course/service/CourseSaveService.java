package com.hsu.oneclickmeet.domain.course.service;

import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest;
import com.hsu.oneclickmeet.domain.course.entity.Course;
import com.hsu.oneclickmeet.domain.course.entity.CourseLibrary;
import com.hsu.oneclickmeet.domain.course.entity.CourseSaveRequest;
import com.hsu.oneclickmeet.domain.course.exception.CourseErrorCode;
import com.hsu.oneclickmeet.domain.course.repository.CourseLibraryRepository;
import com.hsu.oneclickmeet.domain.course.repository.CourseRepository;
import com.hsu.oneclickmeet.domain.course.repository.CourseRequestLock;
import com.hsu.oneclickmeet.domain.course.repository.CourseSaveRequestRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@Validated
@RequiredArgsConstructor
public class CourseSaveService {
	private static final JsonMapper REQUEST_MAPPER = JsonMapper.builder()
			.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
			.build();

	private final CourseRepository courseRepository;
	private final CourseLibraryRepository courseLibraryRepository;
	private final CourseSaveRequestRepository saveRequestRepository;
	private final CourseRequestLock requestLock;

	/** The caller supplies a verified member ID and the place domain's authorized lookup. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public long save(@Positive long memberId, @NotNull UUID requestKey, @Valid @NotNull CreateCourseRequest request,
			@NotNull CoursePlaceLookup placeLookup) {
		String requestHash = fingerprint(request);
		requestLock.acquireSave(memberId, requestKey);
		var previous = saveRequestRepository.findByMemberIdAndRequestKey(memberId, requestKey);
		if (previous.isPresent()) {
			CourseSaveRequest saved = previous.get();
			if (!saved.getRequestHash().equals(requestHash)) {
				throw new BusinessException(CourseErrorCode.IDEMPOTENCY_KEY_CONFLICT);
			}
			return saved.getCourseId();
		}

		Course course = buildCourse(memberId, request, placeLookup);
		courseRepository.save(course);
		courseLibraryRepository.save(CourseLibrary.create(course, memberId));
		saveRequestRepository.save(new CourseSaveRequest(memberId, requestKey, requestHash, course.getId()));
		return course.getId();
	}

	private Course buildCourse(long memberId, CreateCourseRequest request, CoursePlaceLookup placeLookup) {
		List<Course.PlaceInput> places = new ArrayList<>();
		int visitOrder = 1;
		for (var placeRequest : request.places()) {
			long placeId = placeRequest.placeId();
			String name = placeLookup.findPlaceName(memberId, placeId)
					.orElseThrow(() -> new BusinessException(CourseErrorCode.SOURCE_PLACE_NOT_FOUND));
			List<Course.MenuInput> menus = new ArrayList<>();
			int menuOrder = 1;
			for (var menu : placeRequest.menuList()) {
				menus.add(new Course.MenuInput(menu.menuName(), menu.price(), menuOrder++, menu.selected()));
			}
			places.add(new Course.PlaceInput(placeId, name, visitOrder++, menus));
		}
		return Course.create(request.courseName(), places);
	}

	private String fingerprint(CreateCourseRequest request) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(REQUEST_MAPPER.writeValueAsBytes(request));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
		}
	}
}
