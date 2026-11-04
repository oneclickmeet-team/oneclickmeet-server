package com.hsu.oneclickmeet.domain.course.entity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CourseTest {

	@PersistenceContext
	private EntityManager em;

	@Test
	void saveAndReloadsCourse() {
		Course course = Course.create("연남 데이트 코스");

		em.persist(course);
		Long savedId = course.getId();

		em.flush();
		em.clear();

		Course foundCourse = em.find(Course.class, savedId);

		assertThat(foundCourse).isNotNull();
		assertThat(foundCourse.getId()).isEqualTo(savedId);
		assertThat(foundCourse.getName()).isEqualTo("연남 데이트 코스");
		assertThat(course).isNotSameAs(foundCourse);
	}

	@Test
	void preventsExternalChangesToPlaceAndMenuCollections() {
		Course course = Course.create("연남 데이트 코스", List.of(
				new Course.PlaceInput(1L, "파스타 가게", 1, List.of(
						new Course.MenuInput("파스타", 12000, 1, true)
				))
		));
		CoursePlace place = course.getPlaces().getFirst();
		CoursePlaceMenu menu = place.getMenuList().getFirst();

		assertThatThrownBy(() -> course.getPlaces().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> place.getMenuList().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThat(course.getPlaces()).containsExactly(place);
		assertThat(place.getMenuList()).containsExactly(menu);
		assertThat(place.getCourse()).isSameAs(course);
		assertThat(menu.getPlace()).isSameAs(place);
	}
}
