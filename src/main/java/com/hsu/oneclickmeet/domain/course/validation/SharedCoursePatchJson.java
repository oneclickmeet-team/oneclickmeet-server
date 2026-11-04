package com.hsu.oneclickmeet.domain.course.validation;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.util.UUID;

public final class SharedCoursePatchJson {

	private SharedCoursePatchJson() {
	}

	public interface StrictObject {

		@JsonAnySetter
		default void rejectUnknownField(String name, JsonNode ignored) {
			throw new IllegalArgumentException("지원하지 않는 필드입니다: " + name);
		}
	}

	public static final class OptionalNonNullValue extends ValueDeserializer<Object> {

		private final BeanProperty property;
		private final ValueDeserializer<?> delegate;

		public OptionalNonNullValue() {
			this(null, null);
		}

		private OptionalNonNullValue(BeanProperty property, ValueDeserializer<?> delegate) {
			this.property = property;
			this.delegate = delegate;
		}

		@Override
		public ValueDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) {
			return new OptionalNonNullValue(property,
					context.findContextualValueDeserializer(property.getType(), property));
		}

		@Override
		public Object deserialize(JsonParser parser, DeserializationContext context) {
			var type = property.getType();
			if (type.isCollectionLikeType() && !parser.hasToken(JsonToken.START_ARRAY)) {
				return context.reportInputMismatch(property, "JSON 배열이 필요합니다.");
			}
			if (type.hasRawClass(Long.class) && !parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
				return context.reportInputMismatch(property, "JSON 정수가 필요합니다.");
			}
			if ((type.hasRawClass(String.class) || type.hasRawClass(UUID.class)) && !parser.hasToken(JsonToken.VALUE_STRING)) {
				return context.reportInputMismatch(property, "JSON 문자열이 필요합니다.");
			}
			if (type.hasRawClass(UUID.class) && parser.getString().length() != 36) {
				return context.reportInputMismatch(property, "UUID 문자열이 필요합니다.");
			}
			return delegate.deserialize(parser, context);
		}

		@Override
		public Object getNullValue(DeserializationContext context) {
			return context.reportInputMismatch(property, "null을 입력할 수 없습니다.");
		}

		@Override
		public Object getAbsentValue(DeserializationContext context) {
			// Record creator properties must distinguish omission from explicit JSON null.
			return null;
		}
	}
}
