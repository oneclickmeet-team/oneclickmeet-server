package com.hsu.oneclickmeet.domain.course.validation;

import com.hsu.oneclickmeet.domain.course.dto.request.CreateCourseRequest;
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
@Constraint(validatedBy = DistinctCoursePlaces.Validator.class)
public @interface DistinctCoursePlaces {

	String message() default "같은 장소를 두 번 추가할 수 없습니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<DistinctCoursePlaces, CreateCourseRequest> {

		@Override
		public boolean isValid(CreateCourseRequest request, ConstraintValidatorContext context) {
			if (request == null || request.places() == null) {
				return true;
			}
			Set<Long> placeIds = new HashSet<>();
			for (var place : request.places()) {
				if (place != null && place.placeId() != null && !placeIds.add(place.placeId())) {
					context.disableDefaultConstraintViolation();
					context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
						.addPropertyNode("places")
						.addConstraintViolation();
					return false;
				}
			}
			return true;
		}
	}
}
