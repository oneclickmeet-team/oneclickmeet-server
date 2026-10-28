package com.hsu.oneclickmeet.domain.course.entity;

import com.hsu.oneclickmeet.global.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(name = "course")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course extends BaseEntity {

    @Id
    @GeneratedValue
    @Column(name = "course_id")
    private Long id;

    @Column(name = "course_name", nullable = false)
    private String name;

    @OneToMany(mappedBy = "course")
    private List<CourseLibrary> libraries = new ArrayList<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.PERSIST)
    private List<CoursePlace> places = new ArrayList<>();

    @OneToMany(mappedBy = "course")
    private List<SharedCourse> sharedCourses = new ArrayList<>();

    //=== Constructor ===

    @Builder(access = AccessLevel.PRIVATE)
    private Course(String name) {
        this.name = name;
    }

    public static Course create(String name) {
        return Course.builder()
                .name(name)
                .build();
    }

    //=== Domin Method ===

    //TODO: Course Create 메서드를 통해서 한번에 장소와 메뉴가 생성될 수 있도록 메서드 작성

}