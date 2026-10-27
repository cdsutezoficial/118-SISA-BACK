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
- [ ] Cambia el esquema → tabla/columna afectada y si es `NOT NULL`:

## API
- [ ] No cambia endpoints ni respuestas
- [ ] Endpoint nuevo o contrato modificado (DTO, `code` de error) → listar y avisar a quien hace el front:

## Verificación
- [ ] `./mvnw test` en verde
- [ ] Las pruebas de integración (`*IT`) corrieron contra una base local desechable, **nunca contra QA**
- [ ] Probado a mano:

## Cierre
- [ ] `CHANGELOG.md` actualizado
