# Plan: avance de status de AcademicPeriod por fecha (scheduler + endpoint)

## 1. Contexto

Continuación de `2026-07-20-academic-period.md` (máquina de estados secuencial `CONFIGURATION → ENROLLMENT → ACTIVE → CLOSED` del aggregate `AcademicPeriod`, `academic_config`). Ese plan dejó el avance de estado **manual** vía `PATCH /periods/{id}/status`, sin ningún mecanismo para que el ciclo avance solo con el calendario.

Este cambio agrega la contraparte **time-driven**: un job diario (`@Scheduled`) y un endpoint on-demand que avanzan cada periodo por sus fechas límite, reutilizando exactamente la misma máquina `NEXT_STATUS` del PATCH manual.

## 2. Reglas de negocio (mismo ciclo, ahora por calendario)

| Transición | Umbral de fecha |
|---|---|
| CONFIGURATION → ENROLLMENT | `today >= enrollmentStart` |
| ENROLLMENT → ACTIVE | `today >= startDate` |
| ACTIVE → CLOSED | `today > endDate` |

- **Solo hacia adelante**, paso a paso por `NEXT_STATUS` (misma secuencia del PATCH). Si el servidor estuvo apagado o el ciclo simplemente maduró, un solo run puede ponerse al día con varios saltos — es tiempo real transcurrido, no el "skip" de usuario que la regla del PO prohíbe.
- **Nunca retrocede** y **nunca toca un periodo terminal** (`CLOSED`).
- Idempotente: un run que no cambia nada logra "silencio" y no guarda filas.

## 3. Implementación (hexagonal, `academic_config`)

- `domain/model/AcademicPeriod.java` — nuevo método `boolean advanceByDate(LocalDate today)`: walk por `NEXT_STATUS` con el umbral por caso (`ENROLLMENT→enrollmentStart`, `ACTIVE→startDate`, `CLOSED→endDate`, con `today.isAfter` para CLOSED y `!today.isBefore` para el resto). Alcanza por reaplicar `changeStatus(next)` transición a transición; devuelve si algo cambió.
- `domain/port/in/AdvanceAcademicPeriodStatusByDateUseCase.java` — `int advanceAll(LocalDate today)` (el `today` lo inyecta el llamador para poder fijar la fecha en tests; el job pasa `LocalDate.now()`).
- `domain/service/AdvanceAcademicPeriodStatusByDateUseCaseImpl.java` — cascarón fino: `findAll()` → por cada periodo `advanceByDate` → si cambió, `save` y cuenta. `@Transactional`.
- `domain/port/out/AcademicPeriodRepository.java` — nuevo `List<AcademicPeriod> findAll()` (todas las filas, sin paginar, ordenadas `year` ASC + `periodNumber` ASC) — el job debe considerar TODOS los periodos, no una página.
- `infrastructure/persistence/AcademicPeriodRepositoryAdapter.java` — `findAll(Sort.by(year asc).and(periodNumber asc))`.
- `infrastructure/config/UseCaseConfig.java` — bean `advanceAcademicPeriodStatusByDateUseCase` (composition root, mismo patrón de siempre).
- `infrastructure/job/AdvanceAcademicPeriodStatusJob.java` — `@Component` con `@Scheduled(cron = "0 5 0 * * *")` (00:05 diario); loguea solo si `advanced > 0`.
- `CoreApplication.java` — `@EnableScheduling` (habilita `@Scheduled`).
- `infrastructure/web/AcademicPeriodController.java` — `POST /periods/advance-by-date` → `AdvancePeriodsByDateResponse(advanced)`. El front lo llama al abrir el listado para ver estatus frescos sin esperar al job.
- `infrastructure/web/dto/AdvancePeriodsByDateResponse.java` — record `(int advanced)`.
- `identity/infrastructure/security/SecurityFilterConfig.java` — matcher propio `POST /periods/advance-by-date` con `hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")` (mismo gotcha de siempre: cada verbo/patrón nuevo necesita su línea, el wildcard GET no se hereda).

## 4. Tests

- `AcademiaPeriodTest` (+ casos en `AcademicPeriodTest.java`, 114 líneas nuevas en diff): cobertura de `advanceByDate` — transiciones por fecha en cada umbral, catch-up multi-salto lógico, no-regresión cuando la fecha aún no alcanza, periodos `CLOSED` intactos, idempotencia.
- `AdvanceAcademicPeriodStatusByDateUseCaseImplTest` — findAll → muta/salva solo lo que cambia, contador `advanced`.
- `AcademicPeriodRepositoryAdapterSearchIT` — cobertura de `findAll` ordenado.
- `AcademicPeriodControllerTest` — contrato HTTP del endpoint (casos de uso mockeados).
- `AcademicPeriodAdvanceByDateIT` — flujo end-to-end con JWT real: ADMIN/SERVICIOS_ESCOLARES avanzan, roles con 403, sin token 401.

## 5. Config

`application.properties` no agrega fecha ni habilitación del scheduler (el cron es fijo en el job). Ojo: había pasado a tener **defaults de dev** (`20233tn102@utez.edu.mx` / `gocc050221`) vía env var — se mantuvo como cambio **local sin commitear** en `develop` (no se sube; en prod los valores deben venir de `SISA_ADMIN_*`, mismo criterio que `JWT_SECRET`).

## 6. Ejecución (2026-09-08)

Commiteado en `develop` en 4 commits:

1. `feat(academic-config): advance-by-date de AcademicPeriod (dominio, use case y adapter findAll ordenado)` — modelo, puertos, use case + impl, `UseCaseConfig`, adapter `findAll`, tests unit/IT.
2. `feat(academic-config): POST /periods/advance-by-date con matcher de seguridad y tests` — controller, DTO, `SecurityFilterConfig`, `AcademicPeriodControllerTest`, `AcademicPeriodAdvanceByDateIT`.
3. `feat(academic-config): job diario advance-by-date y EnableScheduling` — `AdvanceAcademicPeriodStatusJob` + `CoreApplication`.
4. Este plan.

`application.properties` (defaults dev) queda fuera del repo, solo local.

Verificación: `.\mvnw.cmd verify` — suite completa en verde.