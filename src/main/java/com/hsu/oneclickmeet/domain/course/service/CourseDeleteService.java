package com.hsu.oneclickmeet.domain.course.service;

import com.hsu.oneclickmeet.domain.course.exception.CourseErrorCode;
import com.hsu.oneclickmeet.domain.course.repository.CourseLibraryRepository;
import com.hsu.oneclickmeet.domain.course.repository.CourseRepository;
import com.hsu.oneclickmeet.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseDeleteService {

	private final CourseRepository courseRepository;
	private final CourseLibraryRepository courseLibraryRepository;

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void delete(long memberId, long courseId) {
		courseRepository.findOwnedCourseForUpdate(memberId, courseId)
				.orElseThrow(() -> new BusinessException(CourseErrorCode.NOT_FOUND));
		// Close personal access through its ownership links. Retention of course and memo data is separate.
		if (courseLibraryRepository.deleteOwnedLinks(memberId, courseId) == 0) {
			throw new BusinessException(CourseErrorCode.NOT_FOUND);
		}
	}
}
