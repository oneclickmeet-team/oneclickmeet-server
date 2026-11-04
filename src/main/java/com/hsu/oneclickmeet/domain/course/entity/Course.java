package com.hsu.oneclickmeet.domain.course.entity;

import com.hsu.oneclickmeet.global.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
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
    @OrderBy("position asc") // 조회 시 오름차순 정렬
    private List<CoursePlace> places = new ArrayList<>();

    @OneToMany(mappedBy = "course")
    private List<SharedCourse> sharedCourses = new ArrayList<>();

	private Course(String name) {
		this.name = name;
	}

	public static Course create(String name) {
		return create(name, List.of());
	}

	public static Course create(String name, List<PlaceInput> places) {
		Course course = new Course(name);
		for (PlaceInput place : places) {
			course.places.add(new CoursePlace(course, place));
		}
		return course;
	}

	public List<CoursePlace> getPlaces() {
		return Collections.unmodifiableList(places);
	}

	public record PlaceInput(Long originalPlaceId, String name, Integer position, List<MenuInput> menus) {
	}

	public record MenuInput(String name, Integer price, Integer position, boolean selected) {
	}
}
