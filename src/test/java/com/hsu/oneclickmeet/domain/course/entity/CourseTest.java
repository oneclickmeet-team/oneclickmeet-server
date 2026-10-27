package com.hsu.oneclickmeet.domain.course.entity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CourseTest {

	@PersistenceContext
	private EntityManager em;

	@Test
	void saveAndReloadsCourse() {
		Course course = Course.createCourse("연남 데이트 코스");

		em.persist(course);
		Long savedId = course.getId();

		em.flush();
		em.clear();

		Course findCourse = em.find(Course.class, savedId);

		assertThat(findCourse).isNotNull();
		assertThat(findCourse.getId()).isEqualTo(savedId);
		assertThat(findCourse.getTitle()).isEqualTo("연남 데이트 코스");
		assertThat(course).isNotSameAs(findCourse);
	}
}
