package com.hsu.oneclickmeet.domain.course.repository;

import com.hsu.oneclickmeet.domain.course.entity.Course;
import com.hsu.oneclickmeet.domain.course.entity.CoursePlace;
import com.hsu.oneclickmeet.domain.course.entity.CoursePlaceMenu;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CourseRepositoryTest {

    @Autowired
    private CourseRepository courseRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("코슷 저장 후 재조회 테스트")
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

    @Test
    @DisplayName("코스 저장시 장소와 메뉴를 함께 저장하고 재조회한다.")
    void 장소와_메뉴를_함께_저장() {
        //given
        Course course = Course.create("연남 데이트 코스");
        CoursePlace place = CoursePlace.create(course, "파스타 가게", "창가 자리 예약하기");

        CoursePlaceMenu pasta = CoursePlaceMenu.create(place, "파스타", 12000);
        pasta.isSelected();

        CoursePlaceMenu.create(place, "스테이크", 43000);

        //when
        Course savedCourse = courseRepository.save(course);
        Long savedCourseId = savedCourse.getId();

        em.flush();
        em.clear();

        Course foundCourse = courseRepository.findById(savedCourseId).get();
        CoursePlace foundPlace = foundCourse.getPlaces().getFirst();

        //then
        assertThat(foundCourse).isNotNull();
        assertThat(foundCourse.getName()).isEqualTo("연남 데이트 코스");
        assertThat(foundCourse.getPlaces()).hasSize(1);


        assertThat(foundPlace).isNotNull();
        assertThat(foundPlace.getName()).isEqualTo("파스타 가게");
        assertThat(foundPlace.getMemo()).isEqualTo("창가 자리 예약하기");
        assertThat(foundPlace.getCourse().getId()).isEqualTo(savedCourseId);

        // 장소에 저장된 메뉴의 값 검증
        assertThat(foundPlace.getMenuList())
                .extracting(
                        CoursePlaceMenu::getName,
                        CoursePlaceMenu::getPrice,
                        CoursePlaceMenu::getSelected
                )
                .containsExactlyInAnyOrder(
                        tuple("파스타", 12000, true),
                        tuple("스테이크", 43000, false)
                );


        // 각 메뉴가 재조회한 장소에 연결되어 있는지 검증
        assertThat(foundPlace.getMenuList())
                .allSatisfy(menu ->
                        assertThat(menu.getPlace().getId()).isEqualTo(place.getId()));

    }

}