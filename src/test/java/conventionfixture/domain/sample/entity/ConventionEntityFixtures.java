package conventionfixture.domain.sample.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;

public class ConventionEntityFixtures {
	@Entity
	public static class MutableEntity {
		private String name;
	}

	@MappedSuperclass
	public static class DomainBaseEntity {}
}
