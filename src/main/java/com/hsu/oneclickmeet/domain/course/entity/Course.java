package com.hsu.oneclickmeet.domain.course.entity;

import com.hsu.oneclickmeet.global.entity.BaseEntity;
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

@Column(nullable = false)
private String title;

@OneToMany(mappedBy = "course")
private List<CourseLibrary> courseLibraries= new ArrayList<>();

@OneToMany(mappedBy = "course")
private List<CoursePlace> coursePlaces= new ArrayList<>();

@OneToMany(mappedBy = "course")
private List<SharedCourse> sharedCourses= new ArrayList<>();

//=== Constructor ===

@Builder(access = AccessLevel.PRIVATE)
private Course(String title) {
    this.title= title;
}

public static Course createCourse(String title) {
    return Course.builder()
            .title(title)
            .build();
    }
}