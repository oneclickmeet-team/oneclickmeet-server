package com.hsu.oneclickmeet.domain.course.validation;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public final class CourseRequestScalarDeserializers {

	private CourseRequestScalarDeserializers() {
	}

	public static final class StringValue extends ValueDeserializer<String> {

		@Override
		public String deserialize(JsonParser parser, DeserializationContext context) {
			if (!parser.hasToken(JsonToken.VALUE_STRING)) {
				return context.reportInputMismatch(String.class, "JSON 문자열이 필요합니다.");
			}
			return parser.getString();
		}
	}

	public static final class LongValue extends ValueDeserializer<Long> {

		@Override
		public Long deserialize(JsonParser parser, DeserializationContext context) {
			if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
				return context.reportInputMismatch(Long.class, "JSON 정수가 필요합니다.");
			}
			return parser.getLongValue();
		}
	}

	public static final class IntegerValue extends ValueDeserializer<Integer> {

		@Override
		public Integer deserialize(JsonParser parser, DeserializationContext context) {
			if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
				return context.reportInputMismatch(Integer.class, "JSON 정수가 필요합니다.");
			}
			return parser.getIntValue();
		}
	}

	public static final class BooleanValue extends ValueDeserializer<Boolean> {

		@Override
		public Boolean deserialize(JsonParser parser, DeserializationContext context) {
			if (!parser.hasToken(JsonToken.VALUE_TRUE) && !parser.hasToken(JsonToken.VALUE_FALSE)) {
				return context.reportInputMismatch(Boolean.class, "JSON 불리언이 필요합니다.");
			}
			return parser.getBooleanValue();
		}
	}
}
