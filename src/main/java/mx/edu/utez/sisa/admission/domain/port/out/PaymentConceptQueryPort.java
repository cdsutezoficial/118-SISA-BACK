package mx.edu.utez.sisa.admission.domain.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only out-port over {@code PaymentConcept} (a bounded context NOT owned
 * by admission — the concept catalog lives in {@code academic_config}).
 * {@code admission} resolves the ficha amount from the candidate's program's
 * {@code ADMISSION} concept this way rather than importing
 * {@code academic_config}'s repository port directly (same "own minimal
 * access" rationale as {@code ProgramAdmissionConfigQueryPort}).
 *
 * <p>Strict resolution (Fase 11): registration requires EXACTLY ONE active
 * {@code ADMISSION} concept for the program on the registration date — zero
 * means no ficha can be priced (error), more than one is ambiguous (error).
 * Never falls back to a hardcoded/config amount.
 */
public interface PaymentConceptQueryPort {

	/**
	 * All {@code ACTIVE} {@code ADMISSION} concepts of a program whose
	 * availability window (when set) contains {@code onDate}.
	 */
	List<FichaConcept> findActiveEnrollmentForProgram(UUID programId, LocalDate onDate);

	/**
	 * The same concepts with the availability window <em>not applied</em>.
	 *
	 * <p>This exists purely to tell two failures apart. "The program has no
	 * tuition concept at all" is a catalog misconfiguration the staff has to fix;
	 * "the concept exists but its window has closed" is the normal end of a
	 * sales period, and the applicant deserves to be told the date rather than
	 * sent to a screen about a missing record. With only the date-filtered
	 * query, both arrive as the same empty list and both had to be reported the
	 * same way.
	 */
	List<FichaConcept> findActiveEnrollmentForProgram(UUID programId);

	/**
	 * Minimal pricing projection the admission flow needs. The amount charged
	 * is {@code costExternal} when the concept is external, {@code cost}
	 * otherwise.
	 *
	 * <p>{@code availableFrom}/{@code availableUntil} are the raw catalog
	 * boundaries, {@code null} meaning "open on that side". They are carried
	 * rather than pre-filtered so a caller can report <em>which</em> boundary was
	 * missed; a message that only said "not available" would leave the applicant
	 * with nothing to act on.
	 */
	record FichaConcept(String name, BigDecimal cost, BigDecimal costExternal, boolean isExternal,
			LocalDate availableFrom, LocalDate availableUntil) {
	}
}