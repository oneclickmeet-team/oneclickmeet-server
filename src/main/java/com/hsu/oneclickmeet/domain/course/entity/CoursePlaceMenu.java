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
import lombok.Builder;
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

    @Column(name = "menu_selected", nullable = false)
    private Boolean selected;

    @ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "place_id")
	private CoursePlace place;


    //=== Constroctor ===

    @Builder(access = AccessLevel.PRIVATE)
    private CoursePlaceMenu(CoursePlace place, String name, Integer price, Boolean selected) {
        this.place = place;
        this.name = name;
        this.price = price;
        this.selected = selected;
    }

    public static CoursePlaceMenu create(CoursePlace place, String name, Integer price) {
        CoursePlaceMenu menu = CoursePlaceMenu.builder()
                .place(place)
                .name(name)
                .price(price)
                .selected(false)
                .build();

        menu.connectPlace(place);
        return menu;
    }

    //=== 연관관계 메서드 ===
    private void connectPlace(CoursePlace place) {
        this.place = place;
        place.getMenuList().add(this);
    }

    //=== Domin Method ===

    public void isSelected() {
        this.selected = !selected;
    }


}
