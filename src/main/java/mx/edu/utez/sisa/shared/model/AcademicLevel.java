package mx.edu.utez.sisa.shared.model;

/**
 * Academic offering level shared across every bounded context that needs to
 * classify an {@code AcademicProgram} (e.g. {@code academic_config},
 * Admisión). Defined once in the shared kernel per {@code
 * 00-shared-kernel.md} — no module owns this enum.
 */
public enum AcademicLevel {
	TSU,
	CONTINUIDAD,
	INGENIERIA,
	LICENCIATURA,
	POSGRADO
}
