package com.hsu.oneclickmeet.domain.course.repository;

import com.hsu.oneclickmeet.domain.course.entity.SharedCourse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SharedCourseRepository extends JpaRepository<SharedCourse, Long> {

	boolean existsByCoupleIdAndSourceCourseId(long coupleId, long sourceCourseId);

	boolean existsByIdAndCoupleId(long id, long coupleId);

	Optional<SharedCourse> findByIdAndCoupleId(long sharedCourseId, long coupleId);

	@Query("select shared.couple.id from SharedCourse shared where shared.id = :sharedCourseId")
	Optional<Long> findCoupleId(@Param("sharedCourseId") long sharedCourseId);

	@Query("""
			select shared.id from SharedCourse shared
			where shared.couple.id = :coupleId and shared.id in :sharedCourseIds
			""")
	Set<Long> findSharedCourseIds(@Param("coupleId") long coupleId, @Param("sharedCourseIds") Set<Long> sharedCourseIds);

	@Query("""
			select shared.id as sharedCourseId, course.name as courseName,
			       size(course.places) as placeCount, shared.createdAt as sharedAt
			from SharedCourse shared join shared.course course
			where shared.couple.id = :coupleId
			and (:beforeId is null or shared.createdAt < :beforeSharedAt
				or (shared.createdAt = :beforeSharedAt and shared.id < :beforeId))
			order by shared.createdAt desc, shared.id desc
			""")
	List<Summary> findSummaries(@Param("coupleId") long coupleId,
			@Param("beforeSharedAt") Instant beforeSharedAt, @Param("beforeId") Long beforeId, Pageable limit);

	interface Summary {
		long getSharedCourseId();
		String getCourseName();
		int getPlaceCount();
		Instant getSharedAt();
	}
}
