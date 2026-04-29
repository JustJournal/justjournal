# AGENTS.md

Repository guidance for agents working in `justjournal`.

## Project Overview

- JustJournal is a multi-user blogging web application.
- Stack: Java 17, Spring Boot 3.1.x, Maven 3, MySQL 8, Redis 7.x, and MinIO for avatar storage.
- Packaging: executable Spring Boot JAR built from `pom.xml`.
- Main entry point: `src/main/java/com/justjournal/Application.java`.

## Repository Layout

- `src/main/java/com/justjournal`: application code.
- `src/main/java/com/justjournal/ctl`: MVC and API controllers.
- `src/main/java/com/justjournal/services`: service-layer logic.
- `src/main/java/com/justjournal/repository`: Spring Data repositories.
- `src/main/java/com/justjournal/model`: domain and persistence models.
- `src/main/java/com/justjournal/config`: Spring configuration.
- `src/main/resources`: runtime config and templates.
- `src/main/resources/templates`: server-rendered templates.
- `src/main/resources/static`: static assets, including legacy frontend dependencies.
- `src/test/java/com/justjournal`: unit and integration tests.
- `database`: schema-related SQL and reference queries.
- `rc.d/jj`: example startup script for BSD environments.

## Build And Test

Prefer the Maven wrapper when available.

- Build JAR: `./mvnw package`
- Run tests: `./mvnw test`
- Run the `test` profile explicitly: `./mvnw -Ptest test`
- Run the app locally: `./mvnw spring-boot:run`

Before finishing a change, run the smallest relevant test set first, then broaden if the change touches shared behavior.

## Runtime Notes

- Primary configuration lives in `src/main/resources/application.yml`.
- Logging config is in `src/main/resources/log4j2.xml`.
- ESAPI config is in `src/main/resources/ESAPI.properties`.
- The project expects MySQL 8.x.
- Redis and MinIO are part of the runtime footprint; avoid assuming purely in-memory behavior for features that touch caching or avatar storage.

## Change Guidance

- Keep changes narrow and consistent with the existing Spring Boot structure.
- Prefer extending existing services/repositories/controllers over introducing parallel patterns.
- Preserve public behavior for legacy routes and feeds unless the task explicitly requires a breaking change.
- Be careful in `src/main/resources/static`: it contains vendored or legacy frontend assets that should not be casually reformatted or upgraded.
- If changing SQL-facing models or repository behavior, inspect related files under `database` and relevant integration tests.
- If changing config-sensitive code, verify defaults in `application.yml` and any affected test coverage.

## Testing Guidance

- Unit tests and integration tests both live under `src/test/java/com/justjournal`.
- Many integration-style tests are named with an `IT...` prefix.
- When changing repository, service, RSS, or controller behavior, look for an existing test in the matching package before adding new coverage elsewhere.

## Operational Cautions

- Do not edit generated binaries such as `justjournal-3.0.1.jar`.
- Do not assume the checked-in static component versions should be upgraded as part of unrelated work.
- Avoid repo-wide formatting churn; this codebase mixes older and newer styles.
