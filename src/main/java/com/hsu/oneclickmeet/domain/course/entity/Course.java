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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    @OneToMany(mappedBy = "course", cascade = CascadeType.PERSIST, orphanRemoval = true)
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

	public void replaceContents(String name, List<PlaceChange> changes) {
		Map<Long, CoursePlace> existingPlaces = new HashMap<>();
		places.forEach(place -> existingPlaces.put(place.getId(), place));
		List<CoursePlace> updatedPlaces = new ArrayList<>(changes.size());
		int position = 1;
		for (PlaceChange change : changes) {
			CoursePlace place = change.coursePlaceId() == null
				? new CoursePlace(this, new PlaceInput(change.originalPlaceId(), change.name(), position, List.of()))
				: Objects.requireNonNull(existingPlaces.get(change.coursePlaceId()));
			place.replaceContents(position++, change.menus());
			updatedPlaces.add(place);
		}
		this.name = name;
		places.clear();
		places.addAll(updatedPlaces);
	}

	public void changeName(String name) {
		this.name = name;
	}

	public CoursePlace addPlace(long originalPlaceId, String name, int position) {
		CoursePlace place = new CoursePlace(this, new PlaceInput(originalPlaceId, name, position, List.of()));
		places.add(place);
		return place;
	}

	public void removePlace(CoursePlace place) {
		places.remove(place);
	}

	public Course copyForSharing() {
		List<PlaceInput> copy = places.stream()
				.map(place -> new PlaceInput(place.getOriginalPlaceId(), place.getName(), place.getPosition(),
						place.getMenuList().stream()
								.map(menu -> new MenuInput(menu.getName(), menu.getPrice(), menu.getPosition(), menu.getSelected()))
								.toList()))
				.toList();
		return create(name, copy);
	}

	public record PlaceInput(Long originalPlaceId, String name, Integer position, List<MenuInput> menus) {
	}

	public record MenuInput(String name, Integer price, Integer position, boolean selected) {
	}

	public record PlaceChange(Long coursePlaceId, Long originalPlaceId, String name, List<MenuChange> menus) {
	}

	public record MenuChange(Long menuId, String name, Integer price, boolean selected) {
	}
}
