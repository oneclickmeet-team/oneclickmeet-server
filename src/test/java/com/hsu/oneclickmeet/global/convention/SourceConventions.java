package com.hsu.oneclickmeet.global.convention;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class SourceConventions {

	private SourceConventions() {
	}

	static List<String> violations(Path file, String source) {
		var parser = new JavaParser(new ParserConfiguration()
			.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
		var result = parser.parse(source);
		if (!result.isSuccessful() || result.getResult().isEmpty()) {
			throw new IllegalArgumentException(file + ": " + result.getProblems());
		}
		var unit = result.getResult().orElseThrow();
		boolean dto = unit.getPackageDeclaration()
			.map(declaration -> Arrays.asList(declaration.getNameAsString().split("\\.")).contains("dto"))
			.orElse(false);
		List<String> violations = new ArrayList<>();
		for (var type : unit.findAll(TypeDeclaration.class)) {
			boolean entity = hasAnnotation(unit, type, "jakarta.persistence.Entity")
				|| hasAnnotation(unit, type, "jakarta.persistence.MappedSuperclass");
			if (dto && type instanceof ClassOrInterfaceDeclaration) {
				add(violations, file, type, "DTO_RECORD", "DTO는 record로 선언합니다");
			}
			if ((dto || entity) && hasAnnotation(unit, type, "lombok.Data")) {
				add(violations, file, type, "NO_DATA", "DTO와 엔티티에 Lombok @Data를 사용하지 않습니다");
			}
			if (entity && hasAnnotation(unit, type, "lombok.Setter")) {
				add(violations, file, type, "NO_ENTITY_SETTER", "엔티티에 클래스 전체 @Setter를 사용하지 않습니다");
			}
		}
		return violations;
	}

	private static boolean hasAnnotation(CompilationUnit unit, TypeDeclaration<?> type, String qualifiedName) {
		String simpleName = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
		return type.getAnnotations().stream().anyMatch(annotation -> {
			String name = annotation.getNameAsString();
			if (name.equals(qualifiedName)) {
				return true;
			}
			if (!name.equals(simpleName) || shadowsImport(unit, type, simpleName)) {
				return false;
			}
			var explicitImport = unit.getImports().stream()
				.filter(imported -> !imported.isStatic() && !imported.isAsterisk())
				.map(imported -> imported.getNameAsString())
				.filter(imported -> imported.endsWith("." + simpleName))
				.findFirst();
			if (explicitImport.isPresent()) {
				return explicitImport.get().equals(qualifiedName);
			}
			String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));
			return unit.getImports().stream().anyMatch(imported -> !imported.isStatic()
				&& imported.isAsterisk() && imported.getNameAsString().equals(packageName));
		});
	}

	private static boolean shadowsImport(CompilationUnit unit, TypeDeclaration<?> type, String name) {
		if (unit.getTypes().stream().anyMatch(declaration -> declaration.getNameAsString().equals(name))) {
			return true;
		}
		for (var node = type; node != null; node = node.findAncestor(TypeDeclaration.class).orElse(null)) {
			if (node.getMembers().stream().anyMatch(member -> member instanceof TypeDeclaration<?> nested
				&& nested.getNameAsString().equals(name))) {
				return true;
			}
		}
		return false;
	}

	private static void add(List<String> violations, Path file, TypeDeclaration<?> type, String rule, String message) {
		violations.add(file + ":" + type.getBegin().orElseThrow().line + " [" + rule + "] "
			+ type.getNameAsString() + ": " + message);
	}
}
