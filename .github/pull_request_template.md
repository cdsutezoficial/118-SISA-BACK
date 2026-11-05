## Qué cambia y por qué
<!-- 1–3 líneas. -->

**Historia / incidencia:** HU-XXX-NNN
**Plan:** `docs/plans/...` (si aplica)

## Archivos compartidos
<!-- Si marcas alguno, avisa en el chat del equipo antes de mergear. -->
- [ ] Ninguno
- [ ] `SecurityFilterConfig` / `PermissionRegistry` (ruta o permiso nuevo)
- [ ] `GlobalExceptionHandler` (de cualquier módulo)
- [ ] `UseCaseConfig`
- [ ] `application.properties` / `.env.example` (variable nueva: avisar a infraestructura)
- [ ] Seed runners

## Base de datos
- [ ] No cambia el esquema
- [ ] Cambia el esquema → incluye la migración `db/migration/V…__….sql` (no edita ninguna ya aplicada)
- [ ] Solo agrega (tabla, columna nullable o con `DEFAULT`, índice). Si borra o renombra, va en un segundo PR — ver `docs/guias/migraciones.md`

## API
- [ ] No cambia endpoints ni respuestas
- [ ] Endpoint nuevo o contrato modificado (DTO, `code` de error) → listar y avisar a quien hace el front:

## Verificación
- [ ] `./mvnw verify` en verde contra `sisa_it` local (el candado impide correr contra otra base)
- [ ] Probado a mano:

## Cierre
- [ ] `CHANGELOG.md` actualizado
