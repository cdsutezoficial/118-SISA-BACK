# Recreación de la base de datos — migración PERIODIC_QUOTA (2026-10-02)
#
# Por qué existe este archivo y no una migración de Flyway/Liquibase:
# `spring.jpa.hibernate.ddl-auto=update` (ver .env) NUNCA borra columnas. La
# migración de conceptos y tarifas renombró y eliminó columnas, así que una base
# actualizada con `update` arrastra el esquema viejo indefinidamente. Como el
# proyecto no tiene historial de datos que conservar (la base es de desarrollo y
# la migración es sin backfill), la salida correcta es recrear.
#
# -----------------------------------------------------------------------
# AVISO: esto borra TODOS los datos. Solo sobre una base desechable.
# -----------------------------------------------------------------------
#
# Uso (PowerShell, desde 118-SISA-BACK):
#
#   1. Apuntar DB_URL a una base desechable, NO a `sisa`:
#      $env:DB_URL = "jdbc:mysql://localhost:3306/sisa_dev_reset?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
#
#   2. Recrear el esquema:
#      .\db\recrear-esquema.ps1
#
#   3. Dejar que Hibernate lo construya al arrancar:
#      $env:DB_DDL_AUTO = "create-drop"
#      .\mvnw.cmd spring-boot:run
#
#   4. Verificar que las columnas viejas ya no existen:
#      SELECT column_name FROM information_schema.columns
#       WHERE table_name = 'payment_rate'
#         AND column_name IN ('valid_from', 'valid_to');
#      -- debe devolver 0 filas
#
#      SELECT column_name FROM information_schema.columns
#       WHERE table_name = 'payment_concept' AND column_name = 'is_tuition';
#      -- debe devolver 0 filas
#
# -----------------------------------------------------------------------
# Qué cambió y por qué
# -----------------------------------------------------------------------
#
# payment_rate
#   - is_tuition        -> eliminado (lo reemplazó PaymentConcept.type)
#   - valid_from         -> eliminado (lo reemplazó PaymentRate.status)
#   - valid_to           -> eliminado (idem)
#   + status             -> ACTIVE | INACTIVE
#   + created_at         -> auditoría; "en vigor" ya no es una fecha
#
# payment_concept
#   - is_tuition        -> eliminado
#   + code               -> obligatorio, único (case-insensitive)
#   + level_number       -> nivel del estudiante; sólo para PERIODIC_QUOTA
#   + active_level       -> columna derivada; ver abajo
#
# -----------------------------------------------------------------------
# active_level: cómo se emula un índice único parcial en MySQL 8
# -----------------------------------------------------------------------
#
# La regla de negocio es "como máximo UNA cuota periódica ACTIVA por nivel".
# MySQL 8 no tiene índices filtrados, así que UNIQUE(type, status, level_number)
# sería incorrecto: también rechazaría una segunda cuota INACTIVE del mismo
# nivel, y el historial no es opcional en este modelo.
#
# La columna `active_level` lleva el nivel sólo cuando la fila es una cuota
# periódica activa, y NULL en cualquier otro caso:
#
#     active_level = CASE
#         WHEN type = 'PERIODIC_QUOTA' AND status = 'ACTIVE' THEN level_number
#         ELSE NULL
#     END
#
# UNIQUE sobre una columna nullable funciona porque MySQL considera que los
# NULL son distintos entre sí: pueden existir N filas con NULL y sólo una con
# cada valor de nivel.
#
# En el código, `PaymentConcept.syncActiveLevel()` la mantiene en Java y la
# tabla declara `uk_payment_concept_active_level`. Si alguna vez se cambia esa
# regla, hay que cambiar los dos lados: el nombre de la restricción está
# referenciado en `PaymentConceptRepositoryAdapter` para poder distinguir este
# conflicto de cualquier otro error de integridad y responder 409 en vez de 500.

param(
    [string]$JdbcUrl = $env:DB_URL,
    [string]$Username = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "root" }),
    [string]$Password = $(if ($null -ne $env:DB_PASSWORD) { $env:DB_PASSWORD } else { "" })
)

$ErrorActionPreference = "Stop"

if (-not $JdbcUrl) {
    throw "Falta DB_URL. Ejemplo: jdbc:mysql://localhost:3306/sisa_dev_reset?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
}

if ($JdbcUrl -notmatch "jdbc:mysql://([^:/]+):(\d+)/([^?]+)") {
    throw "No se pudo leer el servidor de '$JdbcUrl'. Se espera jdbc:mysql://host:puerto/base"
}

$host_ = $Matches[1]
$port = $Matches[2]
$database = $Matches[3]

Write-Host "Base a recrear: $database  (en $host_`:$port)" -ForegroundColor Yellow

$confirm = Read-Host "Se borraran TODOS los datos de '$database'. Escribe SI para continuar"
if ($confirm -ne "SI") {
    Write-Host "Cancelado." -ForegroundColor Gray
    exit 0
}

$env:MYSQL_PWD = $Password

try {
    & mysql --host=$host_ --port=$port --user=$Username `
        --execute="DROP DATABASE IF EXISTS ``$database``; CREATE DATABASE ``$database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

    if ($LASTEXITCODE -ne 0) {
        throw "mysql devolvio codigo $LASTEXITCODE"
    }

    Write-Host "Base '$database' recreada vacia." -ForegroundColor Green
    Write-Host "Siguiente paso: arrancar con DB_DDL_AUTO=create-drop para que Hibernate construya el esquema."
    Write-Host "Las columnas is_tuition, valid_from y valid_to no volveran a aparecer."
}
finally {
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
}
