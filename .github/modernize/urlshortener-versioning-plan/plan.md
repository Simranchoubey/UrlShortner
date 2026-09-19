# Modernization Plan — URL Shortener runtime alignment and production hardening

## Scope
This plan focuses on the concrete actions needed to align versioning, improve production readiness, and validate the current working stack without a broad rewrite. The codebase already demonstrates a viable modular-monolith design with Redis, PostgreSQL, Kafka, and JWT security; the main work is to make the runtime contract and operational validation explicit and repeatable.

## Assessment findings

1. The current stack is working and already validated by the project’s automated test suite.
   - Verified evidence: `mvn test -q` exited successfully with status `0`.
   - This means the current Spring Boot + Java configuration is operational for the project as implemented.

2. Versioning is not yet aligned to a single source of truth.
   - The project README states Java 25 and Spring Boot 3.5.
   - The Maven build file declares Spring Boot 3.5.16 and sets `java.version` to `21`.
   - This mismatch creates ambiguity for developers and deployment environments.

3. Production readiness is mostly in place but needs explicit verification on a real Docker host.
   - Docker Compose and Dockerfile exist for PostgreSQL, Redis, Kafka, and the app itself.
   - The project documents the required runtime environment and secret-driven configuration.
   - These assets were authored, but not executed in the current environment because Docker was unavailable here.

4. The architecture is sound and should be preserved.
   - The modular monolith, cache-first redirect flow, PostgreSQL source-of-truth model, and Kafka analytics decoupling are a good fit for the workload.
   - There is no evidence that a rewrite is required; the modernization work should retain the current design and harden it.

## Strategic objective
Keep the current working stack, define one canonical runtime/version contract, and verify the operational behavior for a production-like deployment before expanding scope.

## Planned workstreams

### 1) Align the runtime and versioning contract

Goal: establish one authoritative version declaration for Java and Spring Boot.

Actions:
- Confirm the supported Java baseline in one place: `pom.xml`, CI pipeline, and project docs.
- Decide the production contract as either:
  - Java 21 + Spring Boot 3.5.x, matching the current Maven build and LTS alignment, OR
  - Java 25 + Spring Boot 3.5.x only if the team intentionally targets a non-LTS preview/runtime environment.
- Update the README and any onboarding notes so they reflect the same version as the build.
- Add a short build/version check to CI (for example, `java -version` plus `mvn -q -DskipTests package` or a dedicated validation step).
- Keep the current architecture intact; this is a governance and documentation alignment task, not a framework rewrite.

Success criteria:
- A single runtime target is documented and enforced across docs, build, and CI.
- The team does not have conflicting instructions about Java 21 vs Java 25.

### 2) Harden production readiness without architectural churn

Goal: make the stack ready for real deployment while keeping the existing design.

Actions:
- Validate all secrets and external configuration through `.env.example` and startup validation logic.
- Confirm the JWT secret is enforced as a production requirement and ensure developer defaults are clearly marked as non-production only.
- Review the Docker and Compose setup on a real Docker host to validate the database, Redis, Kafka, and app startup sequence.
- Confirm health and readiness behavior is exposed through Spring Actuator in a production-friendly way.
- Validate that Redis outages degrade gracefully, Kafka publisher failures remain non-blocking for redirects, and the application stays operational under partial dependency outage.
- Review rate-limiting defaults, CORS configuration, and public/private endpoint exposure to ensure secure defaults in real environments.

Success criteria:
- The app starts from a real containerized environment using the documented env values.
- Dependency outages degrade gracefully instead of failing the redirect path.
- The deployment contract is explicit and documented for production use.

### 3) Validate the current working stack end-to-end

Goal: prove the current implementation remains healthy before any further modernization.

Actions:
- Run the existing Maven test suite for the repository as the baseline validation.
- Validate the application with the local database/Redis/Kafka stack using Docker Compose, if available in the target environment.
- Smoke-test the essential runtime paths:
  - registration and login
  - URL creation
  - redirect behavior and expiry handling
  - cache-first lookup and fallback behavior
  - Kafka analytics emission and consumer handling
  - actuator health responses
- Capture the working commands and expected outputs in project docs so future onboarding or deployment validation is repeatable.

Success criteria:
- The stack is validated against the real dependency set.
- The project has a documented validation path that proves the current design is operational.

### 4) Preserve scope and avoid unnecessary rewrites

Goal: keep the modernization focused and minimal.

Actions:
- Treat the current modular monolith as the target architecture for now.
- Avoid broad service decomposition, framework migrations, or replatforming until the current stack has clear operational evidence under real infrastructure.
- Use any future rewrite or migration only when there is a measured performance, scale, or operational need.

Success criteria:
- The project remains a lean, production-standard application instead of entering a speculative full rewrite.

## Recommended execution order

1. Version alignment and source-of-truth cleanup
2. Production readiness validation for Docker and environment contracts
3. End-to-end smoke validation for runtime flows
4. Documentation lock-in and release gate

## Decision gate
Proceed to implementation only after the following are true:
- One Java/Boot version contract is agreed and documented.
- The Docker-based dependency stack is validated on a real Docker host.
- The key production paths are smoke-tested successfully.
- The app continues to pass the project’s automated validation checks.

## Summary
The project is not in need of a broad rewrite. The strongest modernization move is to unify versioning, verify the current runtime stack in a real production-like environment, and document the operational contract so the working design is repeatable and production-safe.
