package com.hsu.oneclickmeet.domain.course.entity;

import com.hsu.oneclickmeet.global.entity.BaseEntity;
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
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

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

    //TODO: 방문 순서는 어떻게 나타내야 할까..

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

	@OneToMany(mappedBy = "place")
	private List<CoursePlaceMenu> menuList = new ArrayList<>();

    //=== Constroctor ===


    //=== Domin Method ===
}
