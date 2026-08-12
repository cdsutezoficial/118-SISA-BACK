package mx.edu.utez.sisa.shared.model;

/**
 * Employment contract type, captured on {@link EmploymentInfo} (source:
 * {@code 118-SISA-CLAUDE/docs/design/dominio/00-shared-kernel.md}; plan:
 * {@code docs/plans/2026-07-29-person-extension.md} — Fase B). Nullable —
 * only meaningful when {@code EmploymentInfo#isEmployed} is {@code true}.
 */
public enum EmploymentType {
	PERMANENT, TEMPORARY
}
