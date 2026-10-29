package conventionfixture.domain.sample.invalid;

import conventionfixture.domain.sample.repository.ConventionRepositoryFixture;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;

public class ConventionMisplacedFixtures {
	@Controller public static class MisplacedController {}
	@Service public static class MisplacedService {}
	@Entity public static class MisplacedEntity {}
	@MappedSuperclass public static class MisplacedMappedSuperclass {}
	public interface MisplacedRepository extends ConventionRepositoryFixture {}
}
