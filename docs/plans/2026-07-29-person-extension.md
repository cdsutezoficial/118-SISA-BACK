# Plan: Extensión de `Person` — Fase B (tablas normalizadas)

## 1. Contexto (docs-first)

Fuente: `118-SISA-CLAUDE/docs/design/dominio/00-shared-kernel.md` (sección `Person` + Value Objects `Address`/`HealthProfile`/`DiversityProfile`/`EmploymentInfo`/`HighSchoolBackground`). Fase B de 3 para soportar el registro de candidatos (RF-ADM-011): Fase A (cerrada, 2026-07-28) sembró los catálogos `State`/`Municipality`/`HighSchoolType`. Esta fase extiende `Person` en sí. Fase C (futura) es `RegisterCandidateUseCase` + `Candidate`, que recién ahí popula todos estos campos.

**Decisión de José (2026-07-28): normalizar en tablas propias, NO usar `@Embeddable`/Value Objects de JPA.** Cada VO del dominio (`Address`, `HealthProfile`, `DiversityProfile`, `EmploymentInfo`, `HighSchoolBackground`) se modela como una entidad JPA con su propia tabla, en relación 1-a-1 opcional con `Person` mediante clave compartida (`person_id` es a la vez PK y FK de la tabla hija) — así "el perfil completo es null" se resuelve de forma nativa (no existe la fila → la relación resuelve a `null`), sin ninguna ambigüedad de embeddables.

## 2. Alcance de esta fase

**Solo el modelo** — extender `Person` + crear las 5 tablas nuevas + los enums nuevos. **Sin casos de uso nuevos.** `CreatePersonUseCase` (ya existente en `identity`, usado por Usuarios/Directores) queda **sin ningún cambio** — sigue llamando al mismo constructor de 5 argumentos que ya usa hoy; todas las columnas/relaciones nuevas quedan `null` para esas personas, que es exactamente el comportamiento esperado (un Director o Docente no pasa por el formulario de candidato).

**Importante**: los campos que el doc marca "No nulo" para `Person` (`birthDate`, `gender`, `nationality`, `maritalStatus`, `hasChildren`) se modelan como **nullable a nivel de columna/JPA** en esta fase — la restricción "no nulo" es una regla de negocio del formulario de candidato (RF-ADM-011), no una restricción de integridad de la tabla `person` en general, porque `Person` es compartida con flujos que nunca la llenan (Usuarios/Directores). Esa validación de obligatoriedad vivirá en `RegisterCandidateUseCase` (Fase C), no en la entidad.

## 3. Modelo

### `Person` — columnas planas nuevas (mismo criterio que las existentes: campos propios de la persona, no ameritan tabla aparte)
`personalEmail`, `mobilePhone`, `homePhone`, `nss`, `birthDate` (LocalDate), `gender` (enum `Gender`), `nationality` (String), `birthStateId`/`birthMunicipalityId` (UUID, sin relación JPA — mismo criterio que el resto del proyecto: referencias cruzadas son UUID planos, nunca `@ManyToOne`/`@JoinColumn` a otro aggregate), `birthForeignState`/`birthForeignMunicipality` (String), `maritalStatus` (enum `MaritalStatus`), `nativeLanguage` (String), `hasChildren` (Boolean, wrapper — no primitivo, para poder representar "sin capturar"), `monthlyFamilyIncome` (BigDecimal), `guardianName`, `guardianEmail`, `emergencyContactPhone` (String) — todas nullable.

### Enums nuevos (`shared/model/`)
- `Gender`: `M, F, NB`
- `MaritalStatus`: `SOLTERO, CASADO, UNION_LIBRE, DIVORCIADO, VIUDO, OTRO`
- `BloodType`: `A_POSITIVE, A_NEGATIVE, B_POSITIVE, B_NEGATIVE, AB_POSITIVE, AB_NEGATIVE, O_POSITIVE, O_NEGATIVE`
- `EmploymentType`: `PERMANENT, TEMPORARY`

### Tablas nuevas — todas con `person_id` como PK **y** FK (relación 1-a-1 opcional vía clave compartida, JPA `@OneToOne` + `@MapsId`)

**`person_address`**: `street`, `exteriorNumber` (no nulos), `interiorNumber` (nullable), `neighborhood`, `locality`, `postalCode` (no nulos), `stateId`/`municipalityId` (UUID nullable, plano — sin relación JPA), `foreignState`/`foreignMunicipality` (String nullable).

**`person_health_profile`**: `hasPreexistingCondition` (boolean), `conditionDescription` (nullable), `hasDisability` (boolean), `disabilityDescription` (nullable), `bloodType` (enum, nullable).

**`person_diversity_profile`**: `parentsSpeakIndigenousLanguage`/`speaksIndigenousLanguage`/`selfIdentifiesIndigenous`/`selfIdentifiesNonBinary`/`belongsToLgbttiqCommunity`/`isAfrodescendant`/`selfIdentifiesAfrodescendant` (boolean), `parentsIndigenousLanguage`/`indigenousLanguage` (String nullable).

**`person_employment_info`**: `isEmployed` (boolean), `employmentType` (enum nullable), `companyName`/`jobTitle`/`workPhone` (nullable), `monthlyIncome` (BigDecimal nullable), `workStartTime`/`workEndTime` (LocalTime nullable).

**`person_high_school_background`**: `schoolName`, `schoolCity` (no nulos), `schoolTypeId` (UUID, plano — FK cruzada a `admission.HighSchoolType`, sin relación JPA por ser de otro bounded context), `gpa` (BigDecimal), `studiedInMexico` (boolean), `schoolStateId`/`schoolMunicipalityId` (UUID nullable, planos), `cct` (String nullable), `cctConfirmed` (Boolean nullable), `foreignCountry`/`schoolForeignState` (String nullable), `academicArea`/`studyPeriod` (String nullable — se capturan en Inscripciones, no en Admisión; quedan siempre null por ahora).

## 4. Relación con `Person` — sin repositorios propios para las 5 tablas nuevas

Las 5 tablas se acceden **exclusivamente a través de `Person`** (`person.getAddress()`, `person.getHealthProfile()`, etc. — cada getter devuelve `null` si no existe la fila hija, gracias al mapeo `@OneToOne(mappedBy=...)` opcional). `Person` ya tiene `cascade = ALL` implícito para estas relaciones, así que guardar un `Person` con un perfil recién asignado persiste ambas filas en una sola transacción. **No se crean `AddressRepository`/`HealthProfileRepository`/etc.** — no hay ningún consumidor que necesite consultarlas independientemente de una `Person` concreta en esta fase (a diferencia de `PaymentRate`, que sí necesitaba repo propio por volumen/consultas cruzadas — acá cada perfil es 0-o-1 por persona, siempre accedido junto con su dueño).

`PersonRepository` (`identity/domain/port/out/`) no necesita ningún método nuevo — `findById`/`save` ya alcanzan; Hibernate resuelve las relaciones vía cascada.

## 5. Fuera de alcance (de esta fase)

- Cualquier caso de uso que popule estos campos (`RegisterCandidateUseCase`, Fase C).
- Validación de "obligatorio para candidatos" — vive en Fase C, no en la entidad.
- Frontend (Fase C, junto con el formulario de registro de candidato).

## 6. Patrón a clonar

Ninguno exacto en este proyecto (primera vez que se usa `@OneToOne` + `@MapsId` con clave compartida) — mirar `AcademicPlan`/`PlanLevel` solo como referencia de "entidad hija con cascada", aunque ahí la relación es `@OneToMany` (colección), no `@OneToOne` (perfil único opcional).

## 7. Verificación

Sin casos de uso nuevos que testear — el foco de los tests es confirmar que el mapeo JPA se comporta como se espera:
- Guardar un `Person` con un `HealthProfile` (o cualquiera de los 5) asignado, recargarlo, confirmar que el getter devuelve los datos correctos.
- Guardar un `Person` SIN ningún perfil asignado, recargarlo, confirmar que los 5 getters devuelven `null` (no un objeto con campos en null) — esta es la garantía central de esta decisión de diseño, hay que probarla explícitamente.
- Confirmar que `CreatePersonUseCaseImpl` (identity, sin cambios) sigue funcionando exactamente igual — correr su suite de tests existente sin modificarla y que siga en verde.

## 8. Execution Log (2026-07-29)

### Archivos creados

- `shared/model/Gender.java`, `MaritalStatus.java`, `BloodType.java`, `EmploymentType.java` — los 4 enums nuevos.
- `shared/model/Address.java`, `HealthProfile.java`, `DiversityProfile.java`, `EmploymentInfo.java`, `HighSchoolBackground.java` — las 5 entidades hijas, cada una `@Entity` + tabla propia (`person_address`, `person_health_profile`, `person_diversity_profile`, `person_employment_info`, `person_high_school_background`), clave compartida `personId` (`@Id` sin `@GeneratedValue`) + `@OneToOne @MapsId @JoinColumn(name = "person_id")` de vuelta a `Person`. Constructor de negocio (sin `person`/`personId`), getters/setters JavaBean planos, `equals`/`hashCode` por `personId` (mismo criterio que el resto de entidades del proyecto: identidad por PK, no por campos).
- `src/test/.../shared/model/PersonProfileRoundTripIT.java` — IT nuevo, `@DataJpaTest` + `TestEntityManager`, 3 casos: (1) `HealthProfile` adjunto round-tripea todos sus campos, (2) `Person` sin ningún perfil → los 5 getters devuelven `null`, (3) las 5 entidades adjuntas simultáneamente round-tripean cada una de forma independiente.

### Archivos modificados

- `shared/model/Person.java`: agregadas 17 columnas planas nuevas (todas nullable) + 5 campos de relación `@OneToOne(mappedBy = "person", cascade = ALL, orphanRemoval = true, fetch = LAZY, optional = true)`. El constructor de 5 argumentos y todos sus call sites (`CreatePersonUseCaseImpl`) quedaron intactos — no se tocó una sola línea de esa parte de la clase.

### Decisiones técnicas / no obvias

- **`optional = true` explícito en el lado `mappedBy` de `Person`**: no es estrictamente necesario para el comportamiento (Hibernate ya trata un `@OneToOne(mappedBy=...)` sin fila hija como ausente/null por defecto), pero se dejó explícito porque es la parte del plan que documenta la garantía central del diseño ("perfil ausente = null nativo") — vale como documentación ejecutable, no solo como configuración.
- **Wiring bidireccional en el setter de `Person`, no en el del hijo**: cada `Person.setXxx(child)` llama `child.setPerson(this)` antes de asignar el campo. Esto es necesario porque el lado propietario de la FK (`@MapsId`) es el hijo (`Address`, `HealthProfile`, etc.) — si no se setea `child.person`, Hibernate no tiene de dónde tomar el `person_id` a insertar (el `@Id` del hijo se deriva de `@MapsId`, que lee la PK del objeto `person` asociado). Sin este wiring, guardar un `Person` con un perfil recién creado fallaría silenciosamente o lanzaría una excepción de PK nula en el hijo.
- **`GenerationType.UUID` en `Person` + `@MapsId` funciona sin problema**: a diferencia de `IDENTITY` (que requiere el round-trip a la BD antes de que el ID exista en memoria), la estrategia `UUID` de Hibernate asigna el identificador en memoria antes del `INSERT`, así que `@MapsId` puede leer `person.getId()` para el hijo en el mismo flush — no hubo que introducir ningún orden de persistencia manual ni `entityManager.flush()` intermedio en el código de producción.
- **Gotcha de test evitado — falso positivo de "null" por caché de primer nivel**: la primera versión mental del test (guardar y leer el mismo objeto `Person` sin `flush()+clear()`) habría pasado aunque el mapeo estuviera mal, porque Hibernate devuelve el mismo objeto en memoria desde el contexto de persistencia. El test real fuerza `TestEntityManager.flush()` + `clear()` antes de releer, así que la aserción de "perfil ausente ⇒ null" prueba una recarga genuina desde H2, no una lectura de caché.
- **No se creó ninguna migración manual**: `spring.jpa.hibernate.ddl-auto=create-drop` (confirmado en `application.properties`) regenera el esquema completo en cada arranque de test/app — coherente con "sin DDL manual" del alcance de esta fase.

### Resultado de tests

- Baseline (antes de esta fase, medido con `./mvnw verify`): **714 tests unitarios / 404 IT**, 0 failures/errors — la cifra "646/404" mencionada en el brief había quedado desactualizada por trabajo de `academic_config` (Generation/AcademicPeriod) posterior; se usó el número real medido, no el del brief.
- `PersonProfileRoundTripIT` (3 tests nuevos): **3/3 verdes**, incluyendo la aserción explícita de "sin perfiles ⇒ los 5 getters son `null`".
- Suite `identity` existente corrida sin modificar: `CreatePersonUseCaseImplTest` (5/5), `PersonRepositoryAdapterSearchIT` (7/7), `PersonControllerIT` (12/12), `UserManagementControllerIT` (17/17), `AuthFlowIT` (5/5), `AdminSeedIT` (2/2) — todos en verde.
- `./mvnw verify` completo (post-cambio): **714 tests unitarios / 407 IT**, 0 failures/errors (IT sube en 3 por `PersonProfileRoundTripIT`; unitarios sin cambio porque esa clase respeta la convención `*IT.java` y corre solo bajo Failsafe). BUILD SUCCESS.

### Desviaciones del brief

- Ninguna funcional. El único ajuste fue usar el conteo baseline real (714/404) en vez del `646/404` citado en el brief, por quedar desactualizado.
