package mx.edu.utez.sisa.admission.domain.model;

/**
 * Lifecycle of a {@link Candidate}, per {@code 118-SISA-CLAUDE/docs/design/
 * dominio/03-admision.md} — "Candidate (Aggregate Root)":
 * {@code REGISTERED → PAID → EXAM_TAKEN → ACCEPTED|REJECTED → ENROLLED}.
 * Transitioning rules live in the admission use cases, not here (same
 * convention as {@code ProgramAdmissionConfigStatus}).
 */
public enum CandidateStatus {
	REGISTERED, PAID, EXAM_TAKEN, ACCEPTED, REJECTED, ENROLLED
}