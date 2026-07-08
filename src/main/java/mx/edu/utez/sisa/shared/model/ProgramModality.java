package mx.edu.utez.sisa.shared.model;

/**
 * Delivery modality of an {@code AcademicProgram} offering, shared across
 * every bounded context that references it. Defined once in the shared
 * kernel per {@code 00-shared-kernel.md} — no module owns this enum.
 */
public enum ProgramModality {
	PRESENCIAL,
	MIXTA
}
