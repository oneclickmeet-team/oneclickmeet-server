package com.hsu.oneclickmeet.domain.course.repository;

import com.hsu.oneclickmeet.domain.course.entity.CourseLibrary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseLibraryRepository extends JpaRepository<CourseLibrary, Long> {
}
