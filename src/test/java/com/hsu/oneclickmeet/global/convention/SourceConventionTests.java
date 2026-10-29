package com.hsu.oneclickmeet.global.convention;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceConventionTests {

	@Test
	void productionSourcesFollowConventions() throws IOException {
		List<Path> files;
		try (var paths = Files.walk(Path.of("src/main/java"))) {
			files = paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
		}
		assertFalse(files.isEmpty(), "검사할 운영 Java 소스가 없습니다");
		List<String> violations = new ArrayList<>();
		for (var file : files) {
			violations.addAll(SourceConventions.violations(file, Files.readString(file)));
		}
		assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("sourceExamples")
	void acceptsAllowedSourcesAndRejectsViolations(String name, String source, List<String> expectedRules) {
		var violations = SourceConventions.violations(Path.of("Example.java"), source);
		assertEquals(expectedRules, violations.stream()
			.map(violation -> violation.substring(violation.indexOf('[') + 1, violation.indexOf(']')))
			.toList());
		violations.forEach(violation -> assertTrue(violation.startsWith("Example.java:")));
	}

	static Stream<Arguments> sourceExamples() {
		return Stream.of(
			Arguments.of("DTO 클래스와 인터페이스 금지", """
				package com.hsu.oneclickmeet.domain.sample.dto.request;
				class CreateRequest {}
				interface Response {}
				""", List.of("DTO_RECORD", "DTO_RECORD")),
			Arguments.of("중첩 DTO 클래스도 검사", """
				package com.hsu.oneclickmeet.domain.sample.dto.response;
				record Response() { static class Item {} }
				""", List.of("DTO_RECORD")),
			Arguments.of("record와 중첩 record 및 지원 enum 허용", """
				package com.hsu.oneclickmeet.domain.sample.dto.response;
				record Response(Item item) {
					record Item(String name) {}
					enum Status { ACTIVE }
				}
				""", List.of()),
			Arguments.of("명시 import의 DTO Data 금지", """
				package com.hsu.oneclickmeet.domain.sample.dto.request;
				import lombok.Data;
				@Data record Request(String name) {}
				""", List.of("NO_DATA")),
			Arguments.of("전체 이름의 엔티티 Data 금지", """
				@jakarta.persistence.Entity @lombok.Data class Sample {}
				""", List.of("NO_DATA")),
			Arguments.of("와일드카드 import의 엔티티 Setter 금지", """
				import jakarta.persistence.*;
				import lombok.*;
				@Entity @Setter class Sample {}
				""", List.of("NO_ENTITY_SETTER")),
			Arguments.of("MappedSuperclass에도 동일 규칙 적용", """
				import jakarta.persistence.MappedSuperclass;
				@MappedSuperclass @lombok.Data @lombok.Setter class Base {}
				""", List.of("NO_DATA", "NO_ENTITY_SETTER")),
			Arguments.of("중첩 엔티티도 검사", """
				class Outer { @jakarta.persistence.Entity @lombok.Setter static class Sample {} }
				""", List.of("NO_ENTITY_SETTER")),
			Arguments.of("엔티티 가변 필드 및 필드 Setter 허용", """
				import jakarta.persistence.Entity;
				import lombok.Setter;
				@Entity class Sample { @Setter private String name; }
				""", List.of()),
			Arguments.of("일반 클래스의 Lombok Data Setter 허용", """
				@lombok.Data @lombok.Setter class Helper {}
				""", List.of()),
			Arguments.of("주석과 문자열은 검사하지 않음", """
				// @Entity @Data @Setter class Fake {}
				class Helper { String text = "@Entity @Data @Setter"; }
				""", List.of()),
			Arguments.of("이름이 같은 사용자 정의 애너테이션 허용", """
				import custom.Entity;
				@Entity @lombok.Data @lombok.Setter class Helper {}
				""", List.of()),
			Arguments.of("명시 import가 와일드카드보다 우선", """
				import jakarta.persistence.Entity;
				import lombok.*;
				import custom.Data;
				import custom.Setter;
				@Entity @Data @Setter class Sample {}
				""", List.of()),
			Arguments.of("지역 애너테이션이 와일드카드보다 우선", """
				import lombok.*;
				@interface Data {}
				@jakarta.persistence.Entity @Data class Sample {}
				""", List.of()),
			Arguments.of("중첩 지역 애너테이션이 와일드카드보다 우선", """
				import lombok.*;
				class Outer {
					@interface Setter {}
					@jakarta.persistence.Entity @Setter class Sample {}
				}
				""", List.of())
		);
	}
}
