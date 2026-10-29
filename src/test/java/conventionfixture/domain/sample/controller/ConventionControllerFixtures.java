package conventionfixture.domain.sample.controller;

import conventionfixture.domain.sample.repository.ConventionRepositoryFixture;
import conventionfixture.domain.sample.service.ConventionServiceFixtures.GoodService;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class ConventionControllerFixtures {
	@RestController
	@RequiredArgsConstructor
	public static class GoodController {
		private final GoodService service;
		private static Object logger;
		@Value("${greeting:hello}") private String greeting;
		private int requestCount;
	}

	@RestController
	public static class FieldInjection {
		@Autowired private GoodService service;
	}

	@RestController
	public static class ResourceInjection {
		@Resource private GoodService service;
	}

	public static class PersistenceInjection {
		@PersistenceContext private EntityManager entityManager;
	}

	public static class PersistenceFactoryInjection {
		@PersistenceUnit private EntityManagerFactory entityManagerFactory;
	}

	@RestController
	public static class MethodInjection {
		@Autowired void setService(GoodService service) {}
	}

	@Autowired
	@Retention(RetentionPolicy.RUNTIME)
	public @interface InjectCollaborator {}

	public static class MetaInjection {
		@InjectCollaborator private GoodService service;
	}

	@RestController
	public static class ExplicitConstructorInjection {
		private final GoodService service;
		@Autowired
		public ExplicitConstructorInjection(GoodService service) { this.service = service; }
	}

	@RestController
	public static class NonFinalDependency {
		private GoodService service;
		public NonFinalDependency(GoodService service) { this.service = service; }
	}

	@RestController
	public static class NonPrivateDependency {
		final GoodService service;
		public NonPrivateDependency(GoodService service) { this.service = service; }
	}

	@RestController
	@RequiredArgsConstructor
	public static class RepositoryDependency {
		private final ConventionRepositoryFixture repository;
	}
}
