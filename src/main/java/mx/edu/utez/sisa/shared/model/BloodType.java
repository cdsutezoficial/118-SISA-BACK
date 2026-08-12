package mx.edu.utez.sisa.shared.model;

/**
 * Blood type, captured on {@link HealthProfile} (source:
 * {@code 118-SISA-CLAUDE/docs/design/dominio/00-shared-kernel.md}; plan:
 * {@code docs/plans/2026-07-29-person-extension.md} — Fase B). Nullable —
 * "not captured" is a valid state.
 */
public enum BloodType {
	A_POSITIVE, A_NEGATIVE, B_POSITIVE, B_NEGATIVE, AB_POSITIVE, AB_NEGATIVE, O_POSITIVE, O_NEGATIVE
}
