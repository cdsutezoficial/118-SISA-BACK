package mx.edu.utez.sisa.admission.domain.model;

/**
 * Lifecycle of a {@link Candidate}, per {@code 118-SISA-CLAUDE/docs/design/
 * dominio/03-admision.md} — "Candidate (Aggregate Root)":
 * {@code REGISTERED → PAID → EXAM_TAKEN → ACCEPTED|REJECTED → ENROLLED}.
 * Transitioning rules live in the admission use cases, not here (same
 * convention as {@code ProgramAdmissionConfigStatus}).
 *
 * <p>{@code PAYMENT_EXPIRED} is the one way off that happy path: a
 * {@code REGISTERED} ficha whose payment window lapsed without payment. It is
 * deliberately <b>not</b> {@code REJECTED} — that state belongs to the academic
 * evaluation later in the sequence, and reusing it for "no pago" would corrupt
 * the admission reports. It only ever comes from {@code REGISTERED}, and a
 * {@code PAID} ficha never expires.
 */
public enum CandidateStatus {
	REGISTERED, PAYMENT_EXPIRED, PAID, EXAM_TAKEN, ACCEPTED, REJECTED, ENROLLED
}