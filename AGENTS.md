# BioCompass PKB Service

## Architecture

Architecture of this service is described in [docs/biocompass_pkb_architecture.md](docs/biocompass_pkb_architecture.md).

The implementation plan is tracked in [docs/pkb_implementation_plan.md](docs/pkb_implementation_plan.md).

## Implementation Workflow

- Implement each GitHub issue on its own separate branch.
- Include the GitHub issue number in the branch name.
- Link the branch or PR back to the corresponding GitHub issue.
- Keep implementation scoped to the current issue unless the user explicitly expands scope.

## Java Stack

- Use Java 25.
- Use Spring Boot 4.0.x and Spring Framework 7.0.x.
- Use Gradle 9.1 or later.
- Use PostgreSQL, Hibernate ORM 7.2.x, and Flyway for persistence work.

## Gradle Wrapper

- Commit `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, and `gradle/wrapper/gradle-wrapper.jar`.
- Do not commit downloaded Gradle distributions, `.gradle/`, or `build/`.
- Prefer adding `distributionSha256Sum` to `gradle-wrapper.properties` when the wrapper distribution checksum is known.

## Local Verification

- Every implementation step should be runnable or testable locally.
- Run the relevant Gradle tests for the step before finalizing.
- When a service change affects startup or HTTP behavior, run the service locally and verify the relevant endpoint.

## Testing

- All implemented code should have unit test coverage.
- Integration and acceptance tests are still needed for infrastructure boundaries, but they do not replace unit tests for domain logic, mappers, validators, policy input construction, event payload construction, and service orchestration.
