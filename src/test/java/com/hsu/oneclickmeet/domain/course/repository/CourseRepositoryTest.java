package com.hsu.oneclickmeet.domain.course.repository;

import com.hsu.oneclickmeet.domain.course.entity.Course;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CourseRepositoryTest {

    @Autowired
    private CourseRepository courseRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    void saveAndReloadsCourse() {
        //given
        Course course = Course.create("연남 데이트 코스");

        //when
        Course savedCourse = courseRepository.save(course);
        Long savedId = savedCourse.getId();

        // DB에 변경 내용을 반영하고 영속성 컨텍스트 비우기
        em.flush();
        em.clear();

        Course foundCourse = courseRepository.findById(savedId)
                .orElseThrow();

        //then
        assertThat(foundCourse).isNotNull();
        assertThat(foundCourse.getId()).isEqualTo(savedId);
        assertThat(foundCourse.getName()).isEqualTo("연남 데이트 코스");
        assertThat(course).isNotSameAs(foundCourse);
    }

}