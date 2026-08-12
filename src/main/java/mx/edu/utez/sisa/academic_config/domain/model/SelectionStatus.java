package mx.edu.utez.sisa.academic_config.domain.model;

/**
 * Controls whether the division director may still edit
 * {@code Candidate.status} (ACCEPTED/REJECTED) for a
 * {@link ProgramAdmissionConfig}'s admission process (docs:
 * {@code 02-config-academica.md} lines 205-226 — "controla si el director de
 * división aún puede modificar aceptados/rechazados"). Every
 * {@link ProgramAdmissionConfig} is created in {@link #IN_REVIEW} and this
 * phase has NO use case that transitions it to {@link #PUBLISHED} —
 * deliberately out of scope (that transition belongs to the future
 * {@code admission} bounded context's {@code PublishAdmissionResultsUseCase},
 * which does not exist yet; plan: {@code docs/plans/2026-07-28-program-admission-config.md}
 * §3/§9). Distinct from {@link ProgramAdmissionConfigStatus}, which controls
 * the ticket-sales window instead.
 */
public enum SelectionStatus {
	IN_REVIEW, PUBLISHED
}
