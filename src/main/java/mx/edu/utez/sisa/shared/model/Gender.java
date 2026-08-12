package mx.edu.utez.sisa.shared.model;

/**
 * Self-reported gender, captured on {@link Person} (source:
 * {@code 118-SISA-CLAUDE/docs/design/dominio/00-shared-kernel.md}; plan:
 * {@code docs/plans/2026-07-29-person-extension.md} — Fase B). Populated by
 * the candidate registration flow (Fase C); {@code null} for any
 * {@code Person} created outside that flow (e.g. staff onboarding via
 * {@code CreatePersonUseCase}).
 */
public enum Gender {
	M, F, NB
}
