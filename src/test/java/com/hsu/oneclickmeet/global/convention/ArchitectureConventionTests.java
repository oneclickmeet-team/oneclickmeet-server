package com.hsu.oneclickmeet.global.convention;

import com.hsu.oneclickmeet.OneclickmeApplication;
import conventionfixture.domain.sample.controller.ConventionControllerFixtures.*;
import conventionfixture.domain.sample.dto.ConventionMisplacedDto;
import conventionfixture.domain.sample.dto.request.ConventionRequestFixture;
import conventionfixture.domain.sample.entity.ConventionEntityFixtures.*;
import conventionfixture.domain.sample.invalid.ConventionMisplacedFixtures.*;
import conventionfixture.domain.sample.repository.ConventionRepositoryFixture;
import conventionfixture.domain.sample.service.ConventionServiceFixtures.*;
import com.hsu.oneclickmeet.global.entity.BaseEntity;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureConventionTests {
	private static final ArchRule FIXTURE_ROLE_PACKAGES = ArchitectureConventions.rolePackages("conventionfixture");
	private static final ArchRule FIXTURE_LAYER_DEPENDENCIES = ArchitectureConventions.layerDependencies("conventionfixture");

	@Test
	void productionCodeFollowsArchitectureConventions() throws Exception {
		Path mainOutput = Path.of(OneclickmeApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		JavaClasses production = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPath(mainOutput);
		assertFalse(production.isEmpty(), "No production classes were imported");
		assertTrue(production.stream().noneMatch(type -> type.getPackageName().contains("conventionfixture")),
			"Negative fixtures must not be included in production checks");
		ArchitectureConventions.ROLE_PACKAGES.check(production);
		ArchitectureConventions.LAYER_DEPENDENCIES.check(production);
		ArchitectureConventions.CONSTRUCTOR_INJECTION.check(production);
	}

	@ParameterizedTest
	@MethodSource("allowedRoleClasses")
	void acceptsAllowedRolePackages(Class<?> fixture) {
		assertFalse(fixture.getPackageName().startsWith(ArchitectureConventions.ROOT),
			"Fixtures must stay outside application component and entity scans");
		assertAccepted(FIXTURE_ROLE_PACKAGES, fixture);
	}

	static Stream<Class<?>> allowedRoleClasses() {
		return Stream.of(GoodController.class, GoodService.class, ConventionRepositoryFixture.class,
			MutableEntity.class, DomainBaseEntity.class,
			ConventionRequestFixture.class, ConventionRequestFixture.NestedRequest.class);
	}

	@ParameterizedTest
	@MethodSource("misplacedRoleClasses")
	void rejectsMisplacedRoles(Class<?> fixture) {
		assertRejected(FIXTURE_ROLE_PACKAGES, fixture);
	}

	@Test
	void acceptsGlobalMappedSuperclass() {
		assertAccepted(ArchitectureConventions.ROLE_PACKAGES, BaseEntity.class);
	}

	@Test
	void rejectsAnnotatedRolesOutsideApplicationRoot() {
		assertRejected(ArchitectureConventions.ROLE_PACKAGES, GoodController.class);
	}

	static Stream<Class<?>> misplacedRoleClasses() {
		return Stream.of(MisplacedController.class, MisplacedService.class, MisplacedRepository.class,
			MisplacedEntity.class, MisplacedMappedSuperclass.class, ConventionMisplacedDto.class);
	}

	@Test
	void acceptsControllerServiceRepositoryDependencyDirection() {
		assertAccepted(FIXTURE_LAYER_DEPENDENCIES, GoodController.class, GoodService.class);
	}

	@ParameterizedTest
	@MethodSource("invertedLayerClasses")
	void rejectsInvertedLayerDependencies(Class<?> fixture) {
		assertRejected(FIXTURE_LAYER_DEPENDENCIES, fixture);
	}

	static Stream<Class<?>> invertedLayerClasses() {
		return Stream.of(RepositoryDependency.class, ControllerDependency.class);
	}

	@Test
	void acceptsLombokConstructorInjectionConfigurationStaticAndMutableEntityFields() {
		assertAccepted(ArchitectureConventions.CONSTRUCTOR_INJECTION,
			GoodController.class, GoodService.class, ExplicitConstructorInjection.class, MutableEntity.class);
	}

	@ParameterizedTest
	@MethodSource("invalidInjectionClasses")
	void rejectsNonConstructorInjectionAndMutableCollaborators(Class<?> fixture) {
		assertRejected(ArchitectureConventions.CONSTRUCTOR_INJECTION, fixture);
	}

	static Stream<Class<?>> invalidInjectionClasses() {
		return Stream.of(FieldInjection.class, ResourceInjection.class, MethodInjection.class,
			PersistenceInjection.class, PersistenceFactoryInjection.class, MetaInjection.class,
			NonFinalDependency.class, NonPrivateDependency.class);
	}

	private static void assertAccepted(ArchRule rule, Class<?>... fixtures) {
		rule.check(new ClassFileImporter().importClasses(fixtures));
	}

	private static void assertRejected(ArchRule rule, Class<?> fixture) {
		assertTrue(rule.evaluate(new ClassFileImporter().importClasses(fixture)).hasViolation(), fixture.getName());
	}
}
