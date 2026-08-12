package mx.edu.utez.sisa.shared.model;

/**
 * Marital status, captured on {@link Person} (source:
 * {@code 118-SISA-CLAUDE/docs/design/dominio/00-shared-kernel.md}; plan:
 * {@code docs/plans/2026-07-29-person-extension.md} — Fase B). Populated by
 * the candidate registration flow (Fase C); {@code null} for any
 * {@code Person} created outside that flow.
 */
public enum MaritalStatus {
	SOLTERO, CASADO, UNION_LIBRE, DIVORCIADO, VIUDO, OTRO
}
