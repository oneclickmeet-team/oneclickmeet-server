package com.hsu.oneclickmeet.domain.course.validation;

import com.hsu.oneclickmeet.domain.course.dto.request.UpdateCourseRequest;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.Set;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DistinctCourseUpdateReferences.Validator.class)
public @interface DistinctCourseUpdateReferences {

	String message() default "같은 장소, 코스 구성 또는 메뉴를 두 번 지정할 수 없습니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<DistinctCourseUpdateReferences, UpdateCourseRequest> {

		@Override
		public boolean isValid(UpdateCourseRequest request, ConstraintValidatorContext context) {
			if (request == null || request.places() == null) {
				return true;
			}
			Set<Long> placeIds = new HashSet<>();
			Set<Long> coursePlaceIds = new HashSet<>();
			Set<Long> menuIds = new HashSet<>();
			for (var place : request.places()) {
				if (place == null) {
					continue;
				}
				if (duplicate(placeIds, place.placeId()) || duplicate(coursePlaceIds, place.coursePlaceId())) {
					return invalid(context);
				}
				if (place.menuList() != null) {
					for (var menu : place.menuList()) {
						if (menu != null && duplicate(menuIds, menu.menuId())) {
							return invalid(context);
						}
					}
				}
			}
			return true;
		}

		private boolean duplicate(Set<Long> ids, Long id) {
			return id != null && !ids.add(id);
		}

		private boolean invalid(ConstraintValidatorContext context) {
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
				.addPropertyNode("places")
				.addConstraintViolation();
			return false;
		}
	}
}
