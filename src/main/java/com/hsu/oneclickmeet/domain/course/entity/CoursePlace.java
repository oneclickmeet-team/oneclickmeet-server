package com.hsu.oneclickmeet.domain.course.entity;

import com.hsu.oneclickmeet.global.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Getter
@Table(name = "course_place")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoursePlace extends BaseEntity {

	@Id
	@GeneratedValue
	@Column(name = "place_id")
	private Long id;

	@Column(name = "place_name", nullable = false)
	private String name;

    @Column(name = "place_memo")
    private String memo;

    @Column(name = "visit_order", nullable = false)
    private Integer position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

	@OneToMany(mappedBy = "place", cascade = CascadeType.PERSIST)
	private List<CoursePlaceMenu> menuList = new ArrayList<>();

    //=== Constroctor ===

    @Builder(access = AccessLevel.PRIVATE)
    private CoursePlace(Course course, String name, String memo, Integer position) {
        this.course = course;
        this.name = name;
        this.memo = memo;
        this.position = position;
    }

    public static CoursePlace create(Course course, String name, Integer position) {

        CoursePlace place = CoursePlace.builder()
                .course(course)
                .name(name)
                .position(position)
                .build();

        place.connectCourse(course);
        return place;
    }

    //=== 연관관계 메서드 ===

    private void connectCourse(Course course) {
        this.course = course;
        course.getPlaces().add(this);
    }

    //=== 도메인 메서드 ===

    public void addMemo(String memo) {
        Objects.requireNonNull(memo, "memo는 null이면 안됩니다.");
        if (memo.isEmpty()) {
            //TODO: 도메인 에러로 변경 필요
            throw new IllegalArgumentException("메모는 빈 문자열을 허용하지 않습니다.");
        }

        this.memo = memo;
    }
}
