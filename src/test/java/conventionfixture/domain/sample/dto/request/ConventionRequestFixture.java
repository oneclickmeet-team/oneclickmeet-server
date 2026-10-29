package conventionfixture.domain.sample.dto.request;

public record ConventionRequestFixture(String name) {
	public record NestedRequest(String value) {}
}
