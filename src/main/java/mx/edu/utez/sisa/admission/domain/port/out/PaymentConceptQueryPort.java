package mx.edu.utez.sisa.admission.domain.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only out-port over {@code PaymentConcept} (a bounded context NOT owned
 * by admission — the concept catalog lives in {@code academic_config}).
 * {@code admission} resolves the ficha amount from the candidate's program's
 * {@code ENROLLMENT} concept this way rather than importing
 * {@code academic_config}'s repository port directly (same "own minimal
 * access" rationale as {@code ProgramAdmissionConfigQueryPort}).
 *
 * <p>Strict resolution (Fase 11): registration requires EXACTLY ONE active
 * {@code ENROLLMENT} concept for the program on the registration date — zero
 * means no ficha can be priced (error), more than one is ambiguous (error).
 * Never falls back to a hardcoded/config amount.
 */
public interface PaymentConceptQueryPort {

	/**
	 * All {@code ACTIVE} {@code ENROLLMENT} concepts of a program whose
	 * availability window (when set) contains {@code onDate}.
	 */
	List<FichaConcept> findActiveEnrollmentForProgram(UUID programId, LocalDate onDate);

	/**
	 * Minimal pricing projection the admission flow needs. The amount charged
	 * is {@code costExternal} when the concept is external, {@code cost}
	 * otherwise.
	 */
	record FichaConcept(String name, BigDecimal cost, BigDecimal costExternal, boolean isExternal) {
	}
}