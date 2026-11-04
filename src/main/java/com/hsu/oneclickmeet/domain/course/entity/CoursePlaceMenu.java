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
@Table(name = "course_place_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoursePlaceMenu extends BaseEntity {

	@Id
	@GeneratedValue
	@Column(name = "menu_id")
	private Long id;

	@Column(name = "menu_name", nullable = false)
	private String name;

	@Column(name = "price", nullable = false)
	private Integer price;

    @Column(name = "menu_order", nullable = false)
    private Integer position;

    @Column(name = "menu_selected", nullable = false)
    private Boolean selected;

    @ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "place_id")
	private CoursePlace place;


	CoursePlaceMenu(CoursePlace place, Course.MenuInput input) {
		this.place = place;
		changeDetails(input);
	}

	void changeDetails(Course.MenuInput input) {
		this.name = input.name();
		this.price = input.price();
		this.selected = input.selected();
		this.position = input.position();
	}

    //=== Domin Method ===

    public void isSelected() {
        this.selected = !selected;
    }


}
