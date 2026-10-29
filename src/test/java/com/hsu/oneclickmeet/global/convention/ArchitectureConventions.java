package com.hsu.oneclickmeet.global.convention;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.properties.HasAnnotations;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.tngtech.archunit.core.domain.JavaModifier.FINAL;
import static com.tngtech.archunit.core.domain.JavaModifier.PRIVATE;
import static com.tngtech.archunit.core.domain.JavaModifier.STATIC;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

final class ArchitectureConventions {

	static final String ROOT = "com.hsu.oneclickmeet";
	private static final String CONTROLLER = "org.springframework.stereotype.Controller";
	private static final String SERVICE = "org.springframework.stereotype.Service";
	private static final String REPOSITORY = "org.springframework.data.repository.Repository";
	private static final Set<String> INJECTION_ANNOTATIONS = Set.of(
		"org.springframework.beans.factory.annotation.Autowired", "jakarta.inject.Inject",
		"javax.inject.Inject", "jakarta.annotation.Resource", "javax.annotation.Resource",
		"jakarta.persistence.PersistenceContext", "jakarta.persistence.PersistenceUnit");

	static final ArchRule ROLE_PACKAGES = rolePackages(ROOT);
	static final ArchRule LAYER_DEPENDENCIES = layerDependencies(ROOT);

	static ArchRule rolePackages(String root) {
		return rule("keep annotated roles and DTOs in their agreed packages", (type, events) -> {
			if (isController(type)) requirePackage(type, "controller", root, events);
			if (annotated(type, SERVICE)) requirePackage(type, "service", root, events);
			if (isRepository(type)) requirePackage(type, "repository", root, events);
			if (type.isAnnotatedWith("jakarta.persistence.Entity")) requirePackage(type, "entity", root, events);
			if (type.isAnnotatedWith("jakarta.persistence.MappedSuperclass")
				&& !type.getPackageName().equals(root + ".global.entity")) requirePackage(type, "entity", root, events);
			if (type.getPackageName().matches(Pattern.quote(root) + "\\.domain\\.[^.]+\\.dto(?:\\..*)?")) {
				if (!inRolePackage(type, "dto\\.(?:request|response)", root)) {
					violation(type, events, type.getName() + " must be in domain.<feature>.dto.request or dto.response");
				}
			}
		});
	}

	static ArchRule layerDependencies(String root) {
		return rule("keep persistence out of controllers and controllers out of services", (type, events) -> {
			for (var dependency : type.getDirectDependenciesFromSelf()) {
				JavaClass target = dependency.getTargetClass();
				boolean controllerToRepository = isController(type) && (isRepository(target) || inRolePackage(target, "repository", root));
				boolean serviceToController = annotated(type, SERVICE) && (isController(target) || inRolePackage(target, "controller", root));
				if (controllerToRepository || serviceToController) violation(type, events, dependency.getDescription());
			}
		});
	}

	static final ArchRule CONSTRUCTOR_INJECTION = rule("use constructor injection and private final collaborator fields", (type, events) -> {
		for (var field : type.getFields()) {
			if (hasInjectionAnnotation(field)) violation(type, events, field.getFullName() + " uses field injection");
		}
		for (var method : type.getMethods()) {
			if (hasInjectionAnnotation(method)) violation(type, events, method.getFullName() + " uses method injection");
		}
		if (!isController(type) && !annotated(type, SERVICE)) return;
		Set<JavaClass> constructorTypes = type.getConstructors().stream()
			.flatMap(constructor -> constructor.getRawParameterTypes().stream()).collect(Collectors.toSet());
		for (JavaField field : type.getFields()) {
			if (field.getModifiers().contains(STATIC) || field.isAnnotatedWith("org.springframework.beans.factory.annotation.Value")) continue;
			JavaClass fieldType = field.getRawType();
			boolean collaborator = constructorTypes.contains(fieldType) && !fieldType.isPrimitive()
				&& !fieldType.isEnum() && !fieldType.getPackageName().equals("java.lang");
			if (collaborator && (!field.getModifiers().contains(PRIVATE) || !field.getModifiers().contains(FINAL))) {
				violation(type, events, field.getFullName() + " is a constructor-injected collaborator and must be private final");
			}
		}
	});

	private static ArchRule rule(String description, BiConsumer<JavaClass, ConditionEvents> check) {
		return classes().should(new ArchCondition<>(description) {
			@Override
			public void check(JavaClass item, ConditionEvents events) {
				check.accept(item, events);
			}
		});
	}

	private static boolean isController(JavaClass type) {
		return annotated(type, CONTROLLER);
	}

	private static boolean isRepository(JavaClass type) {
		return type.isAssignableTo(REPOSITORY) || annotated(type, "org.springframework.stereotype.Repository");
	}

	private static boolean annotated(JavaClass type, String annotation) {
		return type.isAnnotatedWith(annotation) || type.isMetaAnnotatedWith(annotation);
	}

	private static boolean hasInjectionAnnotation(HasAnnotations<?> member) {
		return INJECTION_ANNOTATIONS.stream().anyMatch(annotation ->
			member.isAnnotatedWith(annotation) || member.isMetaAnnotatedWith(annotation));
	}

	private static void requirePackage(JavaClass type, String role, String root, ConditionEvents events) {
		if (!inRolePackage(type, role, root)) violation(type, events, type.getName() + " must be in domain.<feature>." + role);
	}

	private static boolean inRolePackage(JavaClass type, String role, String root) {
		return type.getPackageName().matches(Pattern.quote(root) + "\\.domain\\.[^.]+\\." + role + "(?:\\..*)?");
	}

	private static void violation(JavaClass type, ConditionEvents events, String message) {
		events.add(SimpleConditionEvent.violated(type, message + " (" + type.getSourceCodeLocation() + ")"));
	}
}
