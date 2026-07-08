package mx.edu.utez.sisa.academic_config.domain.model;

/**
 * Lifecycle status of an {@link AcademicProgram}. Deliberately its own enum
 * (design.md — Decision: ProgramStatus is its own enum, NOT shared with
 * {@link DivisionStatus}) — different aggregate lifecycle, sharing would
 * couple unrelated domains.
 */
public enum ProgramStatus {
	ACTIVE,
	INACTIVE
}
