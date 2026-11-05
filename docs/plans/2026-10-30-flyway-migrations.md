# Plan: Migraciones de base de datos con Flyway

> Alcance: todo `118-SISA-BACK` (transversal, no es un bounded context).
> Origen: `118-SISA-CLAUDE/docs/design/pendientes/equipo/2026-10-23-flujo-trabajo-equipo.md` §2.
> Estado: **aprobado por el PO (2026-11-03)** e implementado en la rama `feat/flyway-migraciones`.
> Base de código revisada: `develop` en `13b59fc` (2026-10-30).

## 1. Contexto

Hoy el esquema lo construye Hibernate al arrancar (`spring.jpa.hibernate.ddl-auto=${DB_DDL_AUTO:update}`). Eso trae cuatro problemas que ya ocurrieron:

1. **Los cambios de esquema no quedan versionados.** No hay archivo que revisar en un PR; cada base termina distinta según qué ramas la tocaron.
2. **`update` nunca borra ni relaja.** No quita columnas (por eso existe `db/recrear-esquema.ps1`, de la migración `PERIODIC_QUOTA`), no relaja `NOT NULL` (`app_user.person_id`, `317a5b3`) y no borra tablas (`admission_payment` sigue viva en las bases existentes después de `2aa2ca9`).
3. **Con una base de desarrollo compartida, cada rama modifica el esquema de todos** al levantar su proyecto.
4. **Las pruebas de integración escriben en la base que diga `DB_URL`.** De los 45 `*IT`, 24 llevan `ddl-auto=create-drop` (**tiran y recrean el esquema**) y los otros 21 usan `update` e insertan datos. Si `DB_URL` apunta a la base compartida o a QA, la destruyen o la ensucian.

### Decisiones del PO (2026-10-29)

| Pregunta | Respuesta | Consecuencia |
|---|---|---|
| ¿Hay datos que conservar en QA/beta/prod? | No, todo es de prueba | Todas las bases se **recrean vacías** y nacen con `V1`. Sin `baseline` ni emparejamiento manual |
| ¿El equipo tiene Docker? | No | **Sin Testcontainers.** Las pruebas usan un MySQL local `*_it` con un candado (§3.6) |
| ¿Existe la base de desarrollo compartida? | Sí, ya está lista | Se vacía y la construye Flyway con `V1`; nunca más `update` |

### Inventario a la fecha

- Spring Boot **3.5.16** (Hibernate ORM 6.6), MySQL **8.4** (`docker-compose.yml`), Java 21.
- **40 entidades** en 5 módulos (`academic_config` 15, `identity` 7, `shared` 9, `admission` 4, `finance` 5) + 1 tabla de colección (`payment_concept_linked_concept`).
- IDs: 35 `@GeneratedValue(strategy = UUID)`. Enums: 39 `@Enumerated(STRING)`. 3 `columnDefinition = "TEXT"`.
- Seeds en Java (`ApplicationRunner` con `@Order`): `IdentityAuthorizationCatalogSeedRunner` (0), `StateAndMunicipalitySeedRunner` (1), `PostalCodeSettlementSeedRunner` (2), y los de cuentas y divisiones de prueba (5–15).
- `spring.test.database.replace=none`: los `@DataJpaTest` usan la base real, no una embebida.
- `maven-failsafe-plugin` corre los `*IT` en `./mvnw verify`; `./mvnw test` corre solo los unitarios.

## 2. Alcance

1. Dependencias `flyway-core` + `flyway-mysql`.
2. `ddl-auto=validate` fijo: Hibernate deja de modificar el esquema.
3. `V1__esquema_inicial.sql` con el esquema completo de las 40 entidades.
4. Configuración de Flyway estricta por defecto y relajable por variables de entorno para la base compartida.
5. Pruebas de integración: candado de base `*_it` en `localhost` + Flyway `clean` + `migrate` en lugar de `create-drop`.
6. Guía para el equipo + actualización de `.env.example`, plantilla de PR y `CHANGELOG.md`.
7. Eliminar `db/recrear-esquema.ps1`.
8. Recrear la base compartida y la de QA (coordinado con infraestructura).

**Fuera de alcance:** mover los seeds de datos a migraciones (siguen en Java, §3.5), Testcontainers, cambios de dominio.

## 3. Decisiones de diseño

### 3.1 Flyway y no Liquibase
SQL plano (lo que el equipo ya lee), autoconfiguración nativa de Spring Boot, sin XML/YAML intermedio. Con MySQL 8.x se requiere el módulo aparte `flyway-mysql`; las versiones las administra el parent de Spring Boot.

### 3.2 Hibernate en `validate`, sin variable de entorno
```properties
spring.jpa.hibernate.ddl-auto=validate
```
Se **quita** `DB_DDL_AUTO` del `application.properties` y del `.env.example`. Si se deja configurable, cualquier `.env` viejo (incluido el de Jenkins) vuelve a poner `update` sin que nadie lo note. `validate` solo verifica que exista lo que las entidades necesitan y **tolera tablas y columnas de más**, que es lo que permite trabajar contra una base compartida.

### 3.3 Nombre de las migraciones
```
src/main/resources/db/migration/V{yyyy}_{MM}_{dd}_{HHmm}__{descripcion_en_snake_case}.sql
ej.  V2026_10_30_1200__agrega_payment_method.sql
```
- La fecha y hora evitan que dos personas generen la misma versión en ramas paralelas.
- `V1__esquema_inicial.sql` es la única excepción (versión 1 < cualquier fecha).
- Una migración aplicada **no se edita nunca**: Flyway compara el checksum y no arranca. Los errores se corrigen con una migración nueva.

### 3.4 Estricto por defecto, relajado solo en la base compartida
```properties
spring.flyway.out-of-order=${FLYWAY_OUT_OF_ORDER:false}
spring.flyway.ignore-migration-patterns=${FLYWAY_IGNORE_MIGRATION_PATTERNS:*:future}
```
- **QA, beta y prod:** sin variables → estricto (`*:future` es el default de Flyway).
- **Base de desarrollo compartida** (en el `.env` de cada desarrollador):
  ```
  FLYWAY_OUT_OF_ORDER=true
  FLYWAY_IGNORE_MIGRATION_PATTERNS=*:missing,*:future
  ```
  - `*:missing`: en la base hay migraciones de una rama de otro que mi rama no tiene → no es error.
  - `out-of-order`: mi migración tiene fecha anterior a otra ya aplicada → se aplica igual.

Se usan variables y no un perfil `dev` porque el `.env` se carga con `spring.config.import` y activar perfiles desde un archivo importado no es confiable; las variables siguen el estilo que ya usa el proyecto (`DB_URL`, `SISA_*`).

### 3.5 Los seeds se quedan en Java
INEGI, SEPOMEX, catálogo de permisos y cuentas de prueba son **datos**, no esquema, y dependen de lógica (idempotencia, `SISA_TEST_PASSWORD`, CSV de 159k filas). Flyway corre durante el arranque del contexto, **antes** de cualquier `ApplicationRunner`, así que los seeds ya encuentran las tablas creadas. No cambian.

### 3.6 Pruebas de integración: candado + Flyway en vez de `create-drop`
Sin Docker, la base de pruebas es el MySQL local de cada quien:
```
DB_URL=jdbc:mysql://localhost:3306/sisa_it?createDatabaseIfNotExist=true&...
```
- **Candado:** un `EnvironmentPostProcessor` registrado **solo en `src/test/resources/META-INF/spring.factories`** revisa `spring.datasource.url` antes de levantar cualquier contexto de prueba. Si el host no es `localhost`/`127.0.0.1` o el nombre de la base no termina en `_it`, la prueba falla de inmediato con un mensaje claro. Aplica a los 45 `*IT` sin tocar cada clase.
- **Esquema limpio por contexto:** el mismo post-processor habilita `spring.flyway.clean-disabled=false`, y una `FlywayMigrationStrategy` de prueba hace `clean()` + `migrate()` al levantar cada contexto. Es el equivalente de `create-drop`, pero construyendo el esquema con **las mismas migraciones que producción**: si una migración está mal, fallan las pruebas.
- Se quita `@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")` de las 24 clases que lo tienen.
- En código de producción `clean` sigue deshabilitado (default de Spring Boot).

### 3.7 Cómo se genera `V1`
1. MySQL 8.4 vacío y desechable (`sisa_v1`).
2. Levantar el backend sobre `develop` en un commit fijo con `ddl-auto=create` una única vez, para que Hibernate construya las 40 entidades.
3. `mysqldump --no-data --skip-comments --skip-add-drop-table sisa_v1` → `V1__esquema_inicial.sql`.
4. Limpieza: quitar `AUTO_INCREMENT=`, `DEFINER` y `SET` de sesión del dump; dejar charset/collation explícitos (`utf8mb4` / colación actual de MySQL 8.4).
5. Revisión manual tabla por tabla contra las entidades.
6. Prueba: base vacía → arranque con Flyway + `validate` → debe pasar y los seeds deben correr.

Generarlo desde una base nueva evita arrastrar lo que `update` dejó en las viejas: `admission_payment`, columnas huérfanas de `PERIODIC_QUOTA`, `person_id NOT NULL`.

### 3.8 Enums de MySQL — verificar en la Fase 1
Con MySQL, Hibernate 6.2+ genera las columnas de `@Enumerated(STRING)` como **`ENUM('A','B',…)` nativo** de MySQL, no como `VARCHAR`. Con migraciones eso significa que **agregar un valor a un enum de Java exige una migración** (`ALTER TABLE … MODIFY …`); si se olvida, el `INSERT` falla con "Data truncated". Hay 39 campos así.

Se verifica en la Fase 1 con el dump real y se decide:
- **Opción A — conservar `ENUM` nativo:** sin cambios en entidades; cada valor nuevo = migración. Se documenta en la guía.
- **Opción B — `VARCHAR`:** el dominio puede crecer sin migración; requiere que el mapeo lo pida explícitamente (p. ej. `@JdbcTypeCode(SqlTypes.VARCHAR)` en los 39 campos o una configuración global equivalente, a confirmar con Hibernate 6.6) y que `validate` lo acepte.

Recomendación preliminar: **B**, porque los catálogos de estados siguen cambiando (`QUOTED` → `PENDING` en `9f13ba9`). Se confirma con evidencia antes de escribir `V1`.

**Resuelto (2026-11-03): opción B con un dialecto propio.** Evidencia: con el dialecto estándar Hibernate 6.6.53 generó 38 columnas `ENUM`; `hibernate.type.prefer_native_enum_types=false` no cambia nada en MySQL (solo aplica a tipos enum con nombre, como en PostgreSQL). `SisaMySQLDialect` (`shared/persistence`) registra `VarcharJdbcType` para `SqlTypes.ENUM`: las 38 columnas quedan `VARCHAR(255)`, sin `CHECK`, sin tocar ninguna entidad, y los enums nuevos nacen como `VARCHAR` solos. El resto del esquema es idéntico al del dialecto estándar.

### 3.9 Ambientes (infraestructura)
- El archivo de entorno de Jenkins (credencial `sisa-back-env-cds`) debe **quitar `DB_DDL_AUTO`** y no definir las variables `FLYWAY_*`.
- El usuario de base con el que corre la app necesita privilegios `CREATE, ALTER, DROP, INDEX, REFERENCES` sobre su esquema (hoy Hibernate ya los usa para `update`).
- La base de QA se recrea vacía una vez; al desplegar, Flyway aplica `V1`.
- `beta`/`prod`: igual en su primera liberación con Flyway.

## 4. Regla del equipo para la base compartida

Mientras un PR **no** esté mergeado, sus migraciones **solo agregan**:

| Cambio | ¿Antes del merge? |
|---|---|
| Tabla nueva | ✅ |
| Columna nueva nullable o con `DEFAULT` | ✅ |
| Índice nuevo | ✅ |
| Columna `NOT NULL` sin `DEFAULT` | ❌ |
| Renombrar o borrar columna/tabla | ❌ |
| Cambiar tipo, endurecer restricción, quitar valor de enum | ❌ |

Los cambios ❌ van en **dos PR**: el primero agrega lo nuevo y mueve el código; cuando todos tienen ese código en `develop`, el segundo borra lo viejo. Ejemplo real: `2aa2ca9` (reemplazo de `admission_payment` por el cargo de finanzas) habría sido PR 1 = crear las tablas de finanzas y usar el cargo, PR 2 = `DROP TABLE admission_payment`.

## 5. Fases

| Fase | Qué | Entregable |
|---|---|---|
| **0. Coordinación** | Avisar a Viridiana y Cristian Amauri; acordar un momento sin cambios de esquema a medias; fijar el commit de `develop` del que sale `V1` | Fecha acordada |
| **1. Dependencias y configuración** | `flyway-core` + `flyway-mysql`; `validate` fijo; propiedades `FLYWAY_*`; verificar enums (§3.8) y decidir A/B | PR (sin `V1` aún, no se mergea solo) |
| **2. `V1`** | Generar, limpiar y revisar el esquema inicial (§3.7) | `V1__esquema_inicial.sql` |
| **3. Pruebas** | Candado `*_it` + `clean`/`migrate`; quitar `create-drop` de 24 clases | `./mvnw verify` verde contra `sisa_it` |
| **4. Documentación** | `docs/guias/migraciones.md` (cómo crear una migración, la regla de §4, qué hacer si alguien edita una aplicada); `.env.example`; casilla de BD de la plantilla de PR ("incluye migración `V…`; ¿solo agrega?"); `CHANGELOG.md`; borrar `db/recrear-esquema.ps1` | Docs en el mismo PR |
| **5. Despliegue** | Vaciar la base compartida y la de QA; ajustar el env de Jenkins; primer arranque con Flyway; avisar al equipo que cada quien cree su `sisa_it` local y agregue `FLYWAY_*` a su `.env` | Bases en `V1` |

Fases 1–4 van en **un solo PR** (`feat/flyway-migraciones`), porque por separado ninguna deja el proyecto funcionando: sin `V1`, `validate` no arranca; sin el cambio de pruebas, los `*IT` siguen con `create-drop`.

## 6. Verificación

1. Base vacía → arranque → Flyway aplica `V1` → `validate` pasa → los 7 seeds corren.
2. Segundo arranque sobre la misma base → Flyway no aplica nada; los seeds hacen skip.
3. `./mvnw verify` con `DB_URL` a `sisa_it` local → verde.
4. `./mvnw verify` con `DB_URL` a `sisa` o a un host remoto → **falla de inmediato** con el mensaje del candado, sin tocar la base.
5. Simulación de la base compartida: aplicar una migración de prueba desde una rama y arrancar desde `develop` sin ese archivo → arranca (con `FLYWAY_*` relajado) y falla en modo estricto.
6. Editar una migración ya aplicada → Flyway se niega a arrancar (checksum).

## 7. Riesgos

| Riesgo | Mitigación |
|---|---|
| Ramas abiertas con cambios de esquema cuando se genera `V1` | Fase 0: elegir el momento; quien tenga una rama así la rebasa sobre `develop` y escribe su migración `V2026_…` |
| `V1` no coincide con lo que Hibernate espera (tipos, enums, longitudes) | `validate` lo detecta al arrancar; verificación §6.1 antes del PR |
| Alguien olvida escribir la migración de su cambio de entidad | `validate` impide arrancar con un error que nombra la tabla/columna faltante: falla ruidoso y temprano, no en QA |
| Base compartida acumula restos de ramas abandonadas | No estorba (`validate` tolera lo que sobra); se limpia con una migración de limpieza cuando haga falta |
| El env de Jenkins sigue con `DB_DDL_AUTO=update` | Ya no tiene efecto: la propiedad deja de leer esa variable (§3.2) |

## 8. Pendiente de decidir

- Opción A/B de enums (§3.8), con la evidencia de la Fase 1.
- Quién implementa y en qué fecha (Fase 0).
- Confirmar con infraestructura (Cristian Campos) el cambio del env de Jenkins y la recreación de QA.
