package mx.edu.utez.sisa.academic_config.domain.model;

/**
 * Lifecycle status of an {@link AcademicDivision}. Deliberately its own enum
 * (design.md — Decision: DivisionStatus is its own enum), NOT shared with
 * {@code identity.UserStatus} (which adds {@code LOCKED}) — different
 * aggregate lifecycles, sharing would couple unrelated domains.
 */
public enum DivisionStatus {
	ACTIVE,
	INACTIVE
}
