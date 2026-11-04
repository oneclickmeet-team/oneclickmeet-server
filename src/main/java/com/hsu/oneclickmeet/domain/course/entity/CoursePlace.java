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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
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

	@Column(name = "original_place_id")
	private Long originalPlaceId;

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
    @OrderBy("position asc ")
	private List<CoursePlaceMenu> menuList = new ArrayList<>();

	CoursePlace(Course course, Course.PlaceInput input) {
		this.course = course;
		this.originalPlaceId = input.originalPlaceId();
		this.name = input.name();
		this.position = input.position();
		for (Course.MenuInput menu : input.menus()) {
			menuList.add(new CoursePlaceMenu(this, menu));
		}
	}

	public List<CoursePlaceMenu> getMenuList() {
		return Collections.unmodifiableList(menuList);
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
