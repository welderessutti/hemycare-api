# Hemycare API — Agent Instructions

Act as a senior Java and Spring Boot engineer. Build secure, correct, readable, maintainable software. Apply SOLID,
Clean Code, and hexagonal architecture pragmatically. Prefer established conventions and solutions proportional to the
requirement. Do not overengineer.

## Project Overview

Hemycare is a modular monolith for authenticated users to register, manage, and monitor laboratory examination results.
Its initial scope includes identity and profiles, examination catalogs, records, history, dashboards, and trends.
Preferences, reminders, reports, and notifications may be implemented when requested.

Treat health information as sensitive personal data. Do not introduce automated diagnoses, individualized clinical
recommendations, or treatment decisions without explicit requirements.

## Technology Stack

- Java 25 LTS, Spring Boot 4.1.2, and Gradle Kotlin DSL.
- Spring Security, Spring Data JPA, Hibernate, and Spring Modulith.
- PostgreSQL 18, Flyway, and Docker Compose.
- Jakarta Bean Validation, SpringDoc OpenAPI, and Spring Boot Actuator.
- JUnit, Testcontainers, and existing testing dependencies.

Gradle is authoritative for dependency versions. Prefer official documentation compatible with configured versions. Do
not use Lombok or deprecated APIs. Do not add, remove, or upgrade dependencies, Java, Gradle, or PostgreSQL without
approval.

## Engineering Conventions

- Write code, identifiers, comments, and technical documentation in English.
- Follow Java naming conventions and use descriptive, domain-oriented names.
- Prefer composition, immutability where practical, constructor injection, and cohesive classes and methods.
- Use records for suitable immutable data carriers, enums for defined concepts, appropriate Java time types, and
  `Optional` for absent return values when useful.
- Prefer readable code over clever expressions; avoid ambiguous boolean parameters and unnecessary mutable state.
- Introduce abstractions only for meaningful responsibilities or boundaries. Avoid speculative generalization,
  unnecessary utilities, inheritance hierarchies, and premature optimization.
- Explain non-obvious decisions in comments rather than restating code.

## Architecture

Organize by business module using hexagonal architecture and Spring Modulith. The following is a reference structure,
not mandatory scaffolding:

```text
module/
├── api/                         # Public intermodule contracts, when needed
├── domain/
│   ├── model/
│   └── exception/
├── application/
│   ├── port/{in,out}/
│   ├── command/
│   └── service/
└── infrastructure/
    ├── web/{controller,dto}/
    ├── persistence/{entity,repository,adapter,mapper}/
    └── config/
```

### Domain and Application

Both layers use plain Java and remain independent of Spring, JPA, HTTP, and Infrastructure. Domain must not depend on
Application.

- Domain owns entities, value objects, invariants, behavior, and domain exceptions. Validate invariants during
  construction and state changes. Use domain services only for business behavior that does not naturally belong to a
  domain object.
- Application owns use cases, orchestration, input/output ports, commands, and results. Use small, cohesive contracts
  and keep technical details and HTTP DTOs outside the core.
- Infrastructure implements ports and owns framework integration, controllers, DTOs, persistence, mappers, security,
  email, configuration, transactions, and HTTP exception handling.
- Register Application Services as Spring beans through Infrastructure configuration using standard Spring mechanisms.
  Do not put Spring annotations in the core or use field injection.

### Transactions

Define transaction boundaries at the Infrastructure boundary around complete application use cases. Ensure multi-step
operations are atomic when required, with explicit rollback behavior. Avoid fragmented transaction ownership and
unnecessary distributed coordination. Verify that transaction behavior is actually applied.

### Module Boundaries

Initial modules:

- `identity`: users, profiles, authentication, authorization, and account management.
- `catalog`: examination types, schemas, definitions, associations, and catalog rules.
- `records`: examination records, results, history, and initial trend queries.

Modules own their responsibilities and persistence implementations. Communicate through public Java contracts in `api`
packages when needed, using Spring Modulith `@NamedInterface` metadata outside core classes. Never access another
module's internal repositories, JPA entities, or services. Avoid cycles and internal HTTP calls; prefer synchronous
calls for straightforward interactions.

`records` may consume public `catalog` contracts. Provide the authenticated user identifier through an appropriate
application boundary. Account deletion may require `identity` to coordinate cleanup through public APIs of modules
owning user data. Define exact contracts when implementing the relevant functionality.

Introduce module events only when justified. When using the persistent Event Publication Registry, manage its required
database schema through Flyway according to the configured Spring Modulith version.

## Persistence and Database

### JPA and Domain Separation

- Keep JPA entities, Spring Data repositories, persistence adapters, and mappers in Infrastructure. Access persistence
  through output ports.
- Maintain separate domain and JPA models. Prefer explicit mapping; do not introduce mapping libraries without approval.
- Never expose JPA entities through application ports or public module APIs. Keep ORM lifecycle and fetch strategies
  outside domain models.
- Avoid unnecessary bidirectional relationships, N+1 queries, and loading excessive object graphs. Use queries
  appropriate to each use case.

### Identifier Generation

PostgreSQL generates independent entity primary keys using its native `UUID` type and `DEFAULT uuidv7()`. Use
`java.util.UUID` in Java.

- Independent keys: `app_user`, `exam_type`, `exam_schema`, `exam_definition`, `exam_record`, and `exam_result`.
- Shared key: `profile.user_id` references `app_user.id`.
- Composite key: `exam_schema_definition` uses `(schema_id, definition_id)`. Shared and composite keys reuse existing
  identifiers; do not generate new ones for them.
- Do not generate database-managed identifiers in Java or Hibernate, use external UUID generators, or supply explicit
  IDs for new independent entities without approval.
- New domain entities with database-generated IDs may exist without a persistent identifier. Reconstructed persisted
  entities must have a valid, stable identifier.
- Configure Hibernate to retrieve database-generated identifiers on insertion, using a mapping compatible with the
  project's Hibernate version and PostgreSQL defaults. Do not use `GenerationType.UUID` or assume `GenerationType.AUTO`
  delegates UUID generation to PostgreSQL.
- Return generated identifiers or persisted domain models through persistence ports. Ensure identifiers are available
  before dependent inserts require them. Verify the mapping and identifier retrieval with PostgreSQL integration tests.
- Do not modify persisted identifiers or treat UUIDs as authorization mechanisms.

### Flyway and Schema

- Flyway is authoritative for tables, defaults, constraints, indexes, and triggers. Do not use Hibernate automatic
  schema creation or updates (`create`, `create-drop`, `update`).
- `V1__initial_schema.sql` is the applied PostgreSQL 18 baseline. Do not edit or reformat applied migrations; use a new
  versioned migration for subsequent changes.
- Preserve schema conventions and referential integrity. Do not change schema or constraints without approval.
- Store examination dates separately from creation timestamps; use appropriate numeric types for laboratory results.

### Examination Rules

`ExamType`, `ExamSchema`, `ExamDefinition`, `ExamRecord`, and `ExamResult` must respect the existing schema and
relationships. A record may contain only definitions belonging to its selected schema. Enforce configured value types,
required and optional fields, numeric boundaries, decimal precision, text constraints, and catalog activation rules.
Preserve historical data when catalog entries become inactive.

PostgreSQL triggers will apply and protect `structure_locked` flags for schemas and definitions. Review exact locking
rules before implementing them through a new Flyway migration, before enabling features that depend on locking. Preserve
the distinction between protected structural attributes and editable descriptive or activation attributes. Do not
introduce competing locking mechanisms without reviewing the design.

### Account Deletion

Do not use `ON DELETE CASCADE` for account deletion in the initial implementation. Application must coordinate
authorized deletion of dependent user-owned data in referential order within an appropriate transaction, preserving
shared catalog data. Do not change foreign-key deletion behavior without approval.

## REST API, Validation, and Errors

- Keep controllers thin and delegate through input ports. Follow REST conventions, agreed endpoints and DTO contracts,
  and appropriate HTTP methods and status codes. Do not expose domain or JPA entities directly over HTTP.
- Validate request structure with Jakarta Bean Validation, invariants in Domain, external checks in Application, and
  integrity through database constraints. Do not rely exclusively on frontend or database validation; avoid unnecessary
  duplication.
- Use meaningful domain/application exceptions, generally unchecked, without Spring HTTP dependencies in the core. Avoid
  unnecessary exception hierarchies and exceptions for ordinary control flow.
- Centralize HTTP exception translation in Infrastructure using `@RestControllerAdvice`, `@ExceptionHandler`, and Spring
  `ProblemDetail` (RFC 9457). Preserve consistent validation and error contracts; do not expose sensitive data, stack
  traces, SQL errors, or credentials.

## Security and Privacy

- Use Spring Security. Enforce authorization and resource ownership at the backend; never trust request identifiers or
  frontend guards as proof of ownership.
- Hash passwords appropriately; protect authentication, verification, and reset tokens. Never hardcode credentials or
  commit `.env` files.
- Follow the planned HttpOnly cookie-based authentication architecture. Evaluate Secure, SameSite, CORS, CSRF, token
  rotation, and logout when implementing authentication. Preserve planned session contracts, including `/api/session/me`
  and `/api/session/logout`.
- Do not disable security protections for convenience. Prefer parameterized persistence operations.
- Follow LGPD-oriented privacy and data minimization. Never log credentials, tokens, sensitive personal data, or
  laboratory results.

## Configuration and Observability

- Preserve `application.yml`, profile-specific configuration, and environment-variable secrets. Separate local, test,
  and production environments.
- Keep Docker Compose and Testcontainers aligned on `postgres:18`, not `latest`. Preserve the PostgreSQL 18 storage
  layout (`/var/lib/postgresql`). Do not reset databases, remove persistent volumes, or change production configuration
  without approval.
- Use Spring logging and meaningful diagnostic context for unexpected failures. Avoid `System.out.println` and excessive
  logging. Keep observability and infrastructure proportional to current needs.

## Testing

- Use existing tools. Prefer focused unit tests for domain rules and use cases and integration tests for persistence,
  transactions, authentication, authorization, and integrity.
- Use PostgreSQL 18 through Testcontainers with the Flyway-managed schema. Do not substitute an in-memory database for
  PostgreSQL-specific behavior.
- Verify database-generated UUID v7 IDs without explicit input, Hibernate retrieval of generated IDs, shared/composite
  keys, dependent persistence, and transaction rollback.
- Test migrations and triggers that enforce business invariants. Verify module boundaries with Spring Modulith when
  relevant.
- Do not bypass failing tests or integrity mechanisms. Report environment failures separately from application failures.
  Deferred testing strategy decisions do not excuse leaving critical behavior unverified.

## Scope and Completion

- Implement only requested functionality and modify only relevant files. Preserve architecture, schema, module
  boundaries, API contracts, and established conventions.
- Do not introduce speculative features, empty scaffolding, new infrastructure, or unrelated refactoring. Obtain
  approval for significant architectural changes, dependencies, and migrations; explain trade-offs and clarify ambiguity
  affecting security, architecture, or behavior.
- Before completion, compile using the configured toolchain, run relevant Gradle tasks and tests, verify affected module
  boundaries and core independence, check JPA mappings against Flyway, and review security, validation, transactions,
  errors, and the final diff.
- Remove unused imports and avoid new warnings or unnecessary complexity. Report actual validations and known
  limitations; never claim checks that were not performed.

Prioritize correctness and security, business rules and data integrity, architectural boundaries, readability and
maintainability, simplicity and consistency, and evidence-based performance improvements, in that order.
