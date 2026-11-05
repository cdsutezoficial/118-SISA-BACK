# Guía de migraciones de base de datos (Flyway)

> Desde 2026-11 el esquema de la base **lo construyen las migraciones**, no Hibernate.
> Plan y decisiones: `docs/plans/2026-10-30-flyway-migrations.md`.

## En una frase

Cada cambio de entidad lleva un archivo `.sql` en `src/main/resources/db/migration/`, en el
mismo PR. Al arrancar, Flyway aplica los que falten; Hibernate solo revisa que la base
cuadre con las entidades (`ddl-auto=validate`) y **nunca la modifica**.

## Qué base usa cada cosa

| Para qué | Base | Configuración |
|---|---|---|
| Desarrollar (`spring-boot:run`) | **Compartida** de desarrollo | `DB_URL` en tu `.env` + `FLYWAY_OUT_OF_ORDER=true` y `FLYWAY_IGNORE_MIGRATION_PATTERNS=*:missing,*:future` |
| Pruebas (`./mvnw verify`) | **`sisa_it` en tu MySQL local** | `SISA_IT_DB_URL` (opcional; por defecto `localhost:3306/sisa_it`, se crea sola). Las pruebas **ignoran `DB_URL`** |
| QA, beta, prod | La del ambiente | Sin variables `FLYWAY_*`: Flyway estricto |

Las pruebas **vacían y reconstruyen** su base (Flyway `clean` + `migrate`). Por eso un
candado (`IntegrationTestDatabaseGuard`) las detiene si la base no es `localhost` o
`127.0.0.1` con un nombre terminado en `_it` o `_test`. Usan dos bases que se crean solas:

- `sisa_it`: pruebas con el contexto completo (`@SpringBootTest`). Se limpia **una vez por
  corrida**, para que los catálogos sembrados sobrevivan entre clases.
- `sisa_jpa_it`: pruebas de repositorio (`@DataJpaTest`). Se limpia **en cada contexto**,
  porque esas pruebas cuentan resultados sobre una base vacía.

## Cómo hacer un cambio de esquema

1. **Cambia la entidad**, como siempre.
2. **Crea la migración** en `src/main/resources/db/migration/`:
   ```
   V{yyyy}_{MM}_{dd}_{HHmm}__{descripcion}.sql
   V2026_11_03_1015__agrega_payment_method_a_payment.sql
   ```
   Fecha y hora del momento en que la escribes; **doble guion bajo** antes de la descripción.
   ```sql
   ALTER TABLE payment
     ADD COLUMN payment_method VARCHAR(255) NULL;
   ```
3. **Arranca el backend.** En el log debe aparecer `Successfully applied 1 migration`. Si
   olvidaste la migración, `validate` no deja arrancar y dice qué falta:
   `Schema-validation: missing column [payment_method] in table [payment]`.
4. **Commit de la entidad y del `.sql` juntos** y marca "Cambia el esquema" en el PR.

Para saber qué SQL escribir: el error de `validate` dice qué falta; también se lo puedes pedir
a Claude o Copilot pasándole la entidad. Referencia de tipos que ya usa el esquema:

| Java | MySQL |
|---|---|
| `UUID` (id o referencia) | `BINARY(16)` |
| `String` | `VARCHAR(n)` (`n` = `@Column(length)`, 255 si no se indica) |
| `@Enumerated(STRING)` | `VARCHAR(255)` — **no** `ENUM` (ver abajo) |
| `BigDecimal` | `DECIMAL(p,s)` según `@Column(precision, scale)` |
| `boolean` | `BIT(1)` |
| `LocalDate` / `LocalDateTime` / `Instant` | `DATE` / `DATETIME(6)` |

## La regla de la base compartida: antes del merge, solo se agrega

Tu migración se aplica a la base compartida en cuanto levantas tu rama, aunque tu PR no esté
mergeado. Los demás siguen con el código de `develop`. Por eso:

| Cambio | ¿Antes del merge? | Por qué |
|---|---|---|
| Tabla nueva | ✅ | Nadie más la usa |
| Columna nueva nullable o con `DEFAULT` | ✅ | Los `INSERT` de los demás siguen funcionando |
| Índice nuevo | ✅ | No cambia lo que leen ni escriben |
| Columna `NOT NULL` sin `DEFAULT` | ❌ | Los demás arrancan, pero sus altas en esa tabla fallan |
| Renombrar o borrar columna/tabla | ❌ | A los demás no les arranca el proyecto |
| Cambiar tipo o endurecer una restricción | ❌ | Igual |

Los cambios ❌ van en **dos PR**:
1. Agrega lo nuevo y cambia el código para usarlo; se mergea.
2. Cuando todos ya traen ese código de `develop`, una migración nueva borra lo viejo.

## Reglas

1. **Cambio de entidad = migración en el mismo PR.**
2. **Nunca edites una migración que ya se aplicó** (Flyway compara su checksum y no arranca).
   Los errores se corrigen con una migración nueva.
3. **Pocas sentencias por archivo.** En MySQL los `ALTER TABLE` no se deshacen con rollback:
   si la segunda sentencia falla, la primera ya quedó aplicada.
4. **No hay "revert"**: deshacer un cambio es escribir otra migración.
5. **Los datos semilla no van en migraciones.** INEGI, SEPOMEX, permisos y cuentas de prueba
   siguen en los `*SeedRunner` de Java, que corren después de Flyway.

## Enums como `VARCHAR`

`SisaMySQLDialect` hace que los `@Enumerated(STRING)` se guarden como `VARCHAR` y no como el
`ENUM('A','B')` nativo de MySQL. Así **agregar un valor a un enum de Java no requiere
migración**. Quitar o renombrar un valor sí: los registros que lo usen deben migrarse con un
`UPDATE`, y es un cambio ❌ de la regla anterior.

## Problemas comunes

**`Validate failed: Migration checksum mismatch for migration version …`**
Alguien editó una migración ya aplicada. Regresa el archivo a como estaba
(`git checkout origin/develop -- <archivo>`) y escribe el cambio en una migración nueva.

**`Detected applied migration not resolved locally`**
En la base hay una migración que tu rama no tiene (de la rama de otro). En la base compartida
no debe pasar: revisa que tu `.env` tenga `FLYWAY_IGNORE_MIGRATION_PATTERNS=*:missing,*:future`.

**`Migration … failed` / `Schema … contains a failed migration`**
Una migración falló a medias. Deshaz a mano lo que sí se aplicó, borra su fila con
`success = 0` de `flyway_schema_history`, corrige el `.sql` y vuelve a arrancar.

**`Detected failed migration to version 1` (o falla al crear una llave foránea)**
Revisa los permisos del usuario de la base (`SHOW GRANTS`). Flyway necesita, sobre su
esquema: `SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES`. Sin
`REFERENCES`, las tablas sin llaves foráneas se crean y la primera con llave foránea falla,
dejando la migración a medias. Tras corregir permisos, vacía la base y vuelve a arrancar.

**`Found non-empty schema(s) … but no schema history table`**
La base tiene tablas creadas antes de Flyway (con el viejo `ddl-auto=update`). Vacíala
(`DROP DATABASE` + `CREATE DATABASE`) y deja que Flyway la construya.

**Las pruebas no arrancan: "Las pruebas borran y recrean la base…"**
Es el candado: tu `SISA_IT_DB_URL` (o un `spring.datasource.url` en la prueba) no apunta a una
base local `*_it`/`*_test`. Corrígelo; nunca lo desactives.

## Ver qué está aplicado

```sql
SELECT version, description, installed_by, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
```
