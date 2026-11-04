package com.hsu.oneclickmeet.domain.course.entity;

import com.hsu.oneclickmeet.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "course_library")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseLibrary extends BaseEntity {

	@Id
	@GeneratedValue
	private Long id;

	@Column(nullable = false)
	private String title;

	@Column(name = "member_id")
	private Long memberId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "course_id")
	private Course course;

	private CourseLibrary(Course course, long memberId) {
		this.course = course;
		this.memberId = memberId;
		this.title = course.getName();
	}

	public static CourseLibrary create(Course course, long memberId) {
		CourseLibrary library = new CourseLibrary(course, memberId);
		course.getLibraries().add(library);
		return library;
	}
}
