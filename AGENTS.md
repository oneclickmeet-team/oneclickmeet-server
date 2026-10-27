# Repository Guidelines

Read [CONTRIBUTING.md](CONTRIBUTING.md) and [development conventions](docs/conventions.md) for the detailed team agreements.

## Project Structure & Module Organization

- `src/main/java/com/hsu/oneclickme/`: Spring Boot application code. Organize features under `domain/member`, `domain/place`, etc.; reserve `global` for shared configuration, exception handling, and base entities. Create packages when needed.
- `src/main/resources/`: application configuration and resources; no frontend assets currently exist.
- `src/test/java/`: tests mirroring production packages.
- `.github/`: issue forms and PR template. `docs/`: development conventions.

## Build, Test, and Development Commands

Use Java 21 and the committed Gradle wrapper:

- `./gradlew build`: compile, run tests, and package the application.
- `./gradlew test`: run the test suite.
- `./gradlew test --tests '*OneclickmeApplicationTests'`: run the existing context test.
- `./gradlew bootRun`: start the application locally.

Provide MySQL datasource settings before running the application or context-loading tests. The current `application.yaml` only declares the application name.

## Coding Style & Naming Conventions

Match existing indentation: tabs in Java/Gradle and two spaces in YAML. No formatter or linter is configured yet; avoid unrelated reformatting.

Use `PascalCase` classes, `camelCase` methods/fields, and `UPPER_SNAKE_CASE` constants. Name roles explicitly, such as `PlaceService` and `CreatePlaceRequest`.

Use records for DTOs and ordinary `@Entity` classes with necessary getters and protected no-argument constructors. Do not use Lombok `@Data` or class-wide entity setters. Prefer constructor injection with `private final` dependencies. Return JSON responses in the common `success`/`data`/`error` envelope defined in `docs/conventions.md`.

## Testing Guidelines

Use JUnit Jupiter and Spring Boot testing support. Follow the existing `*Tests` class suffix and descriptive test-method names. For behavior changes, test the relevant success and failure paths. No coverage percentage is configured. Report actual verification results or blockers in the PR.

## Commit & Pull Request Guidelines

Recent history includes `chore: 개발 컨벤션 및 GitHub 협업 템플릿 추가`. Use `feat`, `fix`, `chore`, `docs`, or `refactor` commits without emojis.

Follow GitHub Flow: branch from `main` using `type/issue-number`, such as `feat/12`. Target `main`, use the PR template, link issues, and record verification. PR titles use tags without emojis, for example `[Feat] 로그인 API 제작`; preserve that title in the final Squash commit.

All changes must go through a PR and pass required CI before Squash merge. Use [.github/CODEOWNERS](.github/CODEOWNERS) for domain ownership and review routing. Owners may merge isolated internal changes within agreed API and domain contracts without another approval and remain responsible for the result. Domain entities and DTOs remain owned by their domain implementer; their file type alone does not require joint review.

Changes to another owner's area, jointly reviewed code or infrastructure, SQL, agreed contracts, or behavior affecting other areas require an affected backend developer's approval other than the author's. `global/entity` and its tests are owned by jwon0523 and excluded from path-based joint approval; the owner shares changes or requests additional review when needed. Request review when the impact or ownership is unclear. File ownership alone does not prove that a change is isolated. Automatic approval dismissal remains disabled; request a new review if the approved contract or impact changes. CODEOWNERS does not itself enforce this author-aware policy. GitHub protection settings are reviewed and applied separately; do not bypass existing protections when they differ from the documented policy.

Describe cross-domain impact and review points freely in the PR body, and request review from affected owners when needed. Use GitHub's native required Code Owner review rule together with PR and `Build and Test` requirements. Do not assume owner-authored exemptions have been verified until checked in an actual PR. Do not activate or change GitHub rulesets without explicit user authorization.

## Configuration Status

Docker/Compose and local/dev profiles are planned, not implemented. Initial local development will use `create-drop`; dev schema settings remain undecided and Flyway is deferred. Keep credentials outside Git. The lead will configure CI/CD for automatic dev deployment on `main` merges.
