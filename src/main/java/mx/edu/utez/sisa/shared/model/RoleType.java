package mx.edu.utez.sisa.shared.model;

/**
 * Global roles shared across every bounded context. Defined once in the
 * shared kernel per {@code 00-shared-kernel.md} — no module owns this enum.
 */
public enum RoleType {
	ADMIN,
	SERVICIOS_ESCOLARES,
	GESTOR_ACADEMICO,
	DIRECTOR_DIVISION,
	JEFATURA_ESTADIAS,
	ASISTENTE_ESTADIAS,
	COORDINACION_ESTADIAS_DIVISION,
	PERSONAL_FINANZAS,
	DOCENTE,
	ESTUDIANTE,
	EGRESADO
}
