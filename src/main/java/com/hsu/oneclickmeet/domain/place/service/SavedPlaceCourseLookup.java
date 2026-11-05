package com.hsu.oneclickmeet.domain.place.service;

import com.hsu.oneclickmeet.domain.course.service.CoursePlaceLookup;
import com.hsu.oneclickmeet.domain.place.repository.SavedPlaceRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 코스 저장이 보관함 장소를 참조할 때 쓰는 조회. 본인의 보관함 장소일 때만 이름을 돌려준다.
 * (course 도메인의 {@link CoursePlaceLookup}을 장소 도메인이 구현한다.)
 */
@Component
public class SavedPlaceCourseLookup implements CoursePlaceLookup {

	private final SavedPlaceRepository repository;

	public SavedPlaceCourseLookup(SavedPlaceRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<String> findPlaceName(long memberId, long placeId) {
		return repository.findByIdAndMemberId(placeId, memberId).map(place -> place.getName());
	}
}
