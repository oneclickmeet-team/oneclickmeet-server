package com.hsu.oneclickmeet.domain.comment.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.CodePointLength;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@ReportAsSingleViolation
@NotNull
@Pattern(regexp = "(?s)(?!.*\\p{Cs}).*[^\\p{IsWhite_Space}\\p{javaWhitespace}].*")
@CodePointLength(max = 1000)
public @interface CommentContent {
	String message() default "첨언 내용은 공백만으로 작성할 수 없으며 1자 이상 1,000자 이하여야 합니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
