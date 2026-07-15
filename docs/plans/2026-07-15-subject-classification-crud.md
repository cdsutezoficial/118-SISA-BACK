# Plan: SubjectClassification CRUD (academic_config)

## 1. Contexto (docs-first)

Fuente: `118-SISA-CLAUDE/docs/design/dominio/02-config-academica.md` (líneas 15-24).

`SubjectClassification` es un aggregate propio dentro del bounded context `academic_config`, ya existente en el backend (paquete `mx.edu.utez.sisa.academic_config`, ya contiene `AcademicDivision` y `AcademicProgram`).

> Catálogo de clasificaciones de materias. La clasificación de una materia determina qué escala de calificaciones (equivalencia numérico → letra) se aplica al momento de evaluar. Distintos planes pueden tener equivalencias diferentes para la misma clasificación.

| Atributo | Tipo | Restricciones |
|---|---|---|
| id | UUID | PK |
| name | String | Ej: "Integradora", "Regular" — **no marcado como único en el doc de dominio** (a diferencia de `AcademicDivision.name`) |
| code | String | Único — ej: "INT", "REG" |
| status | Enum | ACTIVE, INACTIVE |

Gap confirmado (sesión 2026-07-15, ver `118-SISA-CLAUDE/docs/design/pendientes/2026-07-15-planes-form-wiring.md`, punto 3): documentado en el dominio, **cero implementación** en `118-SISA-BACK` — ni entidad, ni puerto, ni tabla. `Subject.classificationId` existe como campo pero es *"schema-only, sin validación de existencia"* (javadoc de `Subject.java`). Sin este catálogo, esa referencia no se puede validar de verdad.

`GradeScale`/`GradeScaleEntry` (unicidad `planId + classificationId`) depende de que este catálogo exista primero — queda fuera de este plan, es el siguiente servicio en la cola.

## 2. Patrón a clonar

Mismo template que `AcademicDivision`/`AcademicProgram` (arquitectura hexagonal, mismo módulo `academic_config`):

```
domain/model/SubjectClassification.java + ClassificationStatus.java (enum, o reusar DivisionStatus si el shape calza)
domain/port/in/{Create,Update,List,Get,ChangeStatus}SubjectClassificationUseCase.java
domain/port/out/SubjectClassificationRepository.java
domain/service/{...}UseCaseImpl.java
infrastructure/persistence/SubjectClassificationJpaRepository.java + ...RepositoryAdapter.java
infrastructure/web/SubjectClassificationController.java + dto/*
shared/exception/{DuplicateClassificationCodeException, ClassificationNotFoundException}.java
```

Sin Flyway en este repo — `spring.jpa.hibernate.ddl-auto=create-drop` sobre H2 in-memory (`application.properties`), la tabla sale sola de las anotaciones JPA de la entidad.

Seguridad: mismo patrón de matchers por verbo en `identity/infrastructure/security/SecurityFilterConfig.java` (líneas 83-94), roles `ADMIN` + `SERVICIOS_ESCOLARES` en todos los verbos, igual que `/divisions` y `/programs`. Se agregan 4 líneas nuevas para `/subject-classifications`.

## 3. Entrega servicio por servicio (pedido explícito de José: no un plan/PR gigante — un endpoint, probás, seguimos)

| # | Servicio | Endpoint | Incluye |
|---|---|---|---|
| **1** | **Consulta (List)** | `GET /subject-classifications` | Fundacional: modelo de dominio, tabla (JPA), repositorio (puerto + adapter), matcher de seguridad GET, y el caso de uso List. Sin esto no hay nada que listar — es la base de la que cuelgan los demás. |
| 2 | Registro (Create) | `POST /subject-classifications` | CreateUseCase + validación de `code` único + matcher POST |
| 3 | Detalle (Get by id) | `GET /subject-classifications/{id}` | GetUseCase + 404 si no existe |
| 4 | Actualización (Update) | `PUT /subject-classifications/{id}` | UpdateUseCase + revalidación de `code` único |
| 5 | Cambio de estado | `PATCH /subject-classifications/{id}/status` | ChangeStatusUseCase (ACTIVE ⇄ INACTIVE) |

Cada número = su propia implementación, su propia revisión adversarial, su propio commit. José prueba (Postman/H2 console) antes de pasar al siguiente.

## 4. Fuera de alcance de este plan

- `GradeScale`/`GradeScaleEntry` (depende de que este catálogo exista).
- Wiring de frontend (pantalla de gestión de Clasificaciones en `118-SISA-FRONT` — hoy es dropdown hardcodeado en `MateriasForm.tsx`/`EscalaForm.tsx`). Se planifica aparte una vez que el backend esté completo.
- Validación real de `Subject.classificationId` contra este catálogo (haría falta tocar el aggregate `Subject`, que ya está en producción/mergeado) — decisión de PO separada.

## Execution Log

### Fase 1 — Consulta (List) — COMPLETADA (2026-07-15)

Implementado el aggregate `SubjectClassification` (paquete `mx.edu.utez.sisa.academic_config`) siguiendo exactamente el patrón de `AcademicDivision`, adaptado al shape más simple del catálogo (sin `description`, sin `directorPersonId`, sin `programCount`).

**Archivos creados:**
- `domain/model/SubjectClassification.java` — entidad JPA, `name` NO único (a diferencia de `AcademicDivision.name`), `code` único. Sin `activate()`/`deactivate()`/`updateDetails()` todavía (YAGNI — fases futuras).
- `domain/model/ClassificationStatus.java` — enum propio (ACTIVE, INACTIVE), no reutiliza `DivisionStatus`/`ProgramStatus`.
- `domain/port/in/ListSubjectClassificationsUseCase.java` — query/result/summary records, misma convención de paginación que `ListAcademicDivisionsUseCase` (DEFAULT_PAGE_SIZE=20, MAX_PAGE_SIZE=100).
- `domain/port/out/SubjectClassificationRepository.java` — SOLO el método `search(...)` (YAGNI: sin `save`/`findById`/`findByCode`, se agregan en la fase de Create).
- `domain/service/ListSubjectClassificationsUseCaseImpl.java`.
- `infrastructure/persistence/SubjectClassificationJpaRepository.java` + `SubjectClassificationRepositoryAdapter.java` — tabla `subject_classification` generada por `ddl-auto=create-drop`.
- `infrastructure/web/SubjectClassificationController.java` + `dto/SubjectClassificationListResponse.java` + `dto/SubjectClassificationListItemResponse.java` — SOLO `GET /subject-classifications`.
- Tests: `SubjectClassificationTest`, `ListSubjectClassificationsUseCaseImplTest`, `SubjectClassificationRepositoryAdapterSearchIT`, `SubjectClassificationControllerTest`.

**Archivos modificados:**
- `identity/infrastructure/security/SecurityFilterConfig.java` — agregado matcher GET-only para `/subject-classifications`, `/subject-classifications/**` (roles ADMIN, SERVICIOS_ESCOLARES), sin matchers de POST/PUT/PATCH todavía.
- `academic_config/infrastructure/config/UseCaseConfig.java` — registrado el bean `listSubjectClassificationsUseCase` (composition root del módulo; sin este bean el contexto de Spring no levanta — ver gotcha abajo).

**Decisión de diseño no obvia:** el adapter ordena por `name ASC, code ASC` (no solo `name ASC` como `AcademicDivision`) porque `name` NO es único en este aggregate — el desempate por `code` (que sí es único) garantiza paginación determinística entre páginas.

**Gotcha encontrado:** al clonar el patrón de `AcademicDivision` es fácil olvidar el paso de wiring en `UseCaseConfig.java` (composition root del módulo) — el controlador y el use case impl compilan bien de forma aislada, pero el contexto completo de Spring (`CoreApplicationTests`, `AuthenticationEntryPointTest`, `CorsConfigTest`) falla en tiempo de arranque con `NoSuchBeanDefinitionException` si el `@Bean` no se registra ahí. Los tests con `@WebMvcTest` (que mockean el use case) no detectan este problema porque no cargan el contexto completo — solo aparece corriendo `./mvnw test` sin filtro.

**Resultado de tests:** `./mvnw test` completo → 240 tests, 0 failures, 0 errors, BUILD SUCCESS. `./mvnw clean package -DskipTests` → BUILD SUCCESS.
