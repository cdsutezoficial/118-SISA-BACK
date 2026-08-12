# Project Context — 118-SISA-BACK

## Purpose

Backend for SISAv2 (Sistema Integral de Servicios Académicos), replacing UTEZ's
current SISA (Java/.NET). Manages the full student lifecycle: candidato →
egresado → titulado. Part of the larger SISAv2 workspace
(`C:\workspace\SISAv2`), which also contains `118-SISA-CLAUDE` (requirements
and domain design — source of truth for business rules) and `118-SISA-FRONT`
(React mocked frontend prototype, unrelated tech stack, own completed SDD
history).

Today this repo is a bare Spring Initializr skeleton: `CoreApplication.java`
(empty `@SpringBootApplication` entrypoint) and `CoreApplicationTests.java`
(default `contextLoads()` test only). No modules, no domain code yet.

## Tech Stack

- **Framework**: Spring Boot 3.5.16 (`spring-boot-starter`, `spring-boot-starter-parent`)
- **Language**: Java 21 (`<java.version>21</java.version>` in pom.xml)
- **Build tool**: Maven, via wrapper (`./mvnw`, `./mvnw.cmd`) — Maven 3.9.16
- **Group/Artifact**: `mx.edu.utez.sisa:core:0.0.1-SNAPSHOT`
- **Testing**: `spring-boot-starter-test` (JUnit 5/Jupiter, Mockito, AssertJ, Spring Test) — no other test dependencies yet
- **Quality tools**: none configured (no checkstyle, spotless, jacoco, editorconfig)
- **Scripts/commands**: `./mvnw spring-boot:run` (dev), `./mvnw test`, `./mvnw clean package`

### Environment gotcha — JAVA_HOME (verified this session)

The machine's default `JAVA_HOME` resolves to **Corretto 17.0.13**, but
`pom.xml` requires Java 21 (`release 21`). Running `./mvnw test` or
`./mvnw clean package` with the default environment **fails** with:

```
error: release version 21 not supported
```

A JDK 21 install exists locally at
`C:/Users/JoseNarvaez/.jdks/corretto-21.0.10`. Both `./mvnw test` and
`./mvnw clean package -DskipTests` were verified to **succeed** once
`JAVA_HOME` (and `PATH`) point to that JDK 21 install. Any `sdd-apply` /
`sdd-verify` run in this repo MUST set `JAVA_HOME` to a JDK 21 before running
Maven, or the build/test commands will fail for an environment reason
unrelated to the code under test.

## Architecture

**Monolito Modular + Hexagonal (Ports & Adapters)**, decided in
`118-SISA-CLAUDE/docs/design/00-ARQUITECTURA.md`. Each bounded-context module
follows the same internal layering:

```
{modulo}/
├── domain/
│   ├── model/          ← Entities, Value Objects, Aggregates, Enums
│   ├── port/
│   │   ├── in/          ← Use case interfaces (exposed by the module)
│   │   └── out/         ← Repository / external-service interfaces
│   └── service/         ← Use case implementations (interactors)
├── application/         ← Input/output DTOs, mappers
├── infrastructure/
│   ├── persistence/     ← JPA adapters (implement port/out)
│   ├── web/             ← REST controllers (thin adapters, no business logic)
│   └── external/        ← Adapters for external services (LlaveMX, e-signature)
└── shared/               ← Module-local exceptions, constants, utilities
```

Dependency rule: `domain` ← `application` ← `infrastructure` (inner layers
never know about outer layers).

### Modules and package roots

| Module Java | Bounded Context | Root package |
|---|---|---|
| `identity` | Auth & users | `mx.edu.utez.sisa.identity` |
| `academic-config` | Base config (Módulo 1) | `mx.edu.utez.sisa.academic_config` |
| `admission` | Admisión (Módulo 2) | `mx.edu.utez.sisa.admission` |
| `enrollment` | Inscripciones (Módulo 3) | `mx.edu.utez.sisa.enrollment` |
| `grades` | Calificaciones (Módulo 4) | `mx.edu.utez.sisa.grades` |
| `school-control` | Control Escolar (Módulo 5) | `mx.edu.utez.sisa.school_control` |
| `faculty` | Gestión Docente (Módulo 6) | `mx.edu.utez.sisa.faculty` |
| `finance` | Pagos y Finanzas (Módulo 7) | `mx.edu.utez.sisa.finance` |
| `graduation` | Egreso y Titulación (Módulo 8) | `mx.edu.utez.sisa.graduation` |

Shared kernel (`mx.edu.utez.sisa.shared`): `Person`, `AcademicPeriodRef`,
`StudentRef`, global enums (`AcademicLevel`, `RoleType`, `DocumentStatus`,
`SignatureMode`).

Package root note: the design docs originally used `mx.utez.sisa.*`; this was
corrected to `mx.edu.utez.sisa.*` this session to match the actual `pom.xml`
groupId (`mx.edu.utez.sisa`). This is the confirmed, deliberate package root —
not an error to flag in future phases.

### Cross-module communication

Modules never couple directly. Two mechanisms only:

1. **Query ports** — a module consumes another module's output port via DI (e.g. `FinanceQueryPort.hasActiveDebt(studentId)`).
2. **Domain events** (Spring Application Events) — async reactions across modules (e.g. `GradeRecordClosedEvent`).

### Conventions

| Element | Convention |
|---|---|
| IDs | UUID v4 for all entities |
| Use case | One `XxxUseCase` class implements one `XxxPort` (in) interface |
| Controllers | Thin adapters — no business logic |
| JPA repositories | Implement output ports |
| Transactions | Only in the service layer (use cases) |
| Code language | English — entities, methods, variables, comments |
| Documentation language | Spanish |

## Testing

- **Test runner**: `./mvnw test` — verified working (with JDK 21, see gotcha above)
- **Framework**: JUnit 5 / Jupiter via `spring-boot-starter-test` (bundles Mockito, AssertJ, Spring Test)
- **Current tests**: only the default `CoreApplicationTests.contextLoads()` scaffold test — no real tests yet
- **Coverage**: not configured (no jacoco)
- **Strict TDD Mode**: **enabled** — see Testing Capabilities below

## Testing Capabilities

**Strict TDD Mode**: enabled
**Detected**: 2026-07-05

### Test Runner

- Command: `./mvnw test` (requires `JAVA_HOME` pointed at a JDK 21 — see gotcha above)
- Framework: JUnit 5 / Jupiter (via `spring-boot-starter-test`: JUnit Jupiter, Mockito, AssertJ, Spring Test)

### Test Layers

| Layer       | Available | Tool        |
| ----------- | --------- | ----------- |
| Unit        | ✅        | JUnit 5 + Mockito |
| Integration | ✅        | `@SpringBootTest` / Spring Test (via spring-boot-starter-test) |
| E2E         | ❌        | —           |

### Coverage

- Available: ❌
- Command: —

### Quality Tools

| Tool         | Available | Command        |
| ------------ | --------- | -------------- |
| Linter       | ❌        | —              |
| Type checker | ✅        | `javac` (compiler plugin, release 21, runs as part of `./mvnw compile`/`test`) |
| Formatter    | ❌        | —              |

## SDD History

- This `sdd-init` run is the FIRST SDD initialization for this repo. No prior openspec usage, no prior Engram context. Repo has no git commits yet (initial scaffold is staged, not committed).

## Persistence

- **Mode**: hybrid (openspec files + Engram).
- **Engram `project`**: `118-sisa-claude` — the SISAv2 workspace umbrella project (all repos under `C:\workspace\SISAv2` share this Engram project; the umbrella folder itself is not a git repo).
- **Engram topic_key convention**: repo-scoped, `sdd-init/{repo-name}` — this repo uses `sdd-init/118-sisa-back` (consistent with `118-SISA-FRONT` using `sdd-init/118-sisa-front`).
