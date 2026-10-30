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

## Code Review Rules

`docs/conventions.md`의 기존 합의와 `CONTRIBUTING.md`의 리뷰 기준을 근거로 변경 코드를 검토한다. AI 리뷰는 보조 의견이며, AI의 승인·리뷰 완료·대화 해결을 병합 조건으로 요구하지 않는다.

### 검토 범위

- 업무 코드가 해당 `domain`에 있는지, 공통 역할이 불분명한 코드를 `global`이나 `common`에 모으지 않았는지 확인한다. CI의 패키지 검사는 역할의 위치만 확인하며 업무 소유권까지 판단하지 않는다.
- Controller가 요청 바인딩·검증·서비스 호출·응답 구성을 담당하는지, 업무 판단과 저장 처리가 섞이지 않았는지 확인한다.
- 생성자 주입과 협력 객체의 `private final`을 확인한다. Lombok `@RequiredArgsConstructor`도 허용하며 엔티티의 모든 필드에 `final`을 요구하지 않는다.
- Service는 구체 클래스로 시작한다. 인터페이스 분리의 필요성은 계약·구현 교체 요구를 근거로 판단한다. 모든 Service의 인터페이스화나 인터페이스 일괄 금지, Clean Architecture 도입을 요구하지 않는다.
- 요청·응답 DTO 분리, 엔티티 직접 응답 여부, 변경 의도가 드러나는 엔티티 메서드를 확인한다. 명확한 record·Lombok 규칙은 CI 검사 결과와 중복해 지적하지 않는다.
- 입력의 null·빈 목록·경계값, 예외 누락, HTTP 상태와 공통 응답의 일치 여부를 확인한다.
- 인증·소유자 확인 누락, 다른 사용자의 데이터 접근 경로와 API·도메인 계약 변경의 영향을 확인한다.
- JPA 매핑, 저장·삭제 책임, 부분 저장, 조회 쿼리, 중복 요청·경쟁 조건에서 구체적인 위험이 보이면 발생 경로를 제시한다. 합의하지 않은 트랜잭션·동시성 정책을 새 필수 규칙으로 만들지 않는다.
- 변경한 동작의 성공·실패·경계 사례에서 빠진 검증을 확인한다. 실행하지 않은 테스트나 운영 효과를 확인된 결과처럼 표현하지 않는다.

### 의견 작성

리뷰 제목·요약·인라인 코멘트와 발견한 문제가 없을 때의 설명은 한국어로 작성한다. 코드 식별자·명령어·오류 문자열은 원문을 유지한다.

수정할 가치가 있는 문제와 근거만 간결하게 쓴다. 코드 위치와 발생 조건, 실제 영향 또는 위반한 기존 규칙을 함께 제시한다. 추론이나 확인이 필요한 부분은 단정하지 않는다.

같은 문제는 대표 위치에서 묶어 전달한다. 취향 수준의 제안, 정형적인 교육 설명, 이해 확인 질문, 팀 내 질문·조율 절차는 덧붙이지 않는다. 합의한 컨벤션 위반과 선택적인 개선 의견을 구분한다.

## Configuration Status

Docker/Compose and local/dev profiles are planned, not implemented. Initial local development will use `create-drop`; dev schema settings remain undecided and Flyway is deferred. Keep credentials outside Git. The lead will configure CI/CD for automatic dev deployment on `main` merges.
