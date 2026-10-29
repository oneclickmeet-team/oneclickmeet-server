package conventionfixture.domain.sample.service;

import conventionfixture.domain.sample.controller.ConventionControllerFixtures.GoodController;
import conventionfixture.domain.sample.repository.ConventionRepositoryFixture;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

public class ConventionServiceFixtures {
	@Service
	@RequiredArgsConstructor
	public static class GoodService {
		private final ConventionRepositoryFixture repository;
	}

	@Service
	@RequiredArgsConstructor
	public static class ControllerDependency {
		private final GoodController controller;
	}
}
