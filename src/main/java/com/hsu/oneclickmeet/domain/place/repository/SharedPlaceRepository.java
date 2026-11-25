package com.hsu.oneclickmeet.domain.place.repository;

import com.hsu.oneclickmeet.domain.place.entity.PlaceProvider;
import com.hsu.oneclickmeet.domain.place.entity.SharedPlace;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SharedPlaceRepository extends JpaRepository<SharedPlace, Long> {

	Optional<SharedPlace> findByCouple_IdAndProviderAndProviderPlaceId(
			long coupleId, PlaceProvider provider, String providerPlaceId);

	@Query("""
			select p from SharedPlace p
			where p.couple.id = :coupleId and (:beforeId is null or p.id < :beforeId)
			order by p.id desc
			""")
	List<SharedPlace> findPage(@Param("coupleId") long coupleId, @Param("beforeId") Long beforeId, Pageable pageable);
}
