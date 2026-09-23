package mx.edu.utez.sisa.admission.domain.port.out;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only out-port over {@code ProgramAdmissionConfig} (a bounded context
 * NOT owned by admission — it lives in {@code academic_config}).
 * {@code admission} validates the applicant's chosen program window by
 * looking up the config this way rather than importing
 * {@code academic_config}'s repository port directly (same "own minimal
 * access" rationale as {@code CandidatePersonRepository} / academic_config's
 * {@code PersonLookupJpaRepository}). Cap-resistant (paid-ficha count vs
 * {@code maxCandidates}) validation is deferred until {@code AdmissionPayment}
 * exists (plan §5).
 */
public interface ProgramAdmissionConfigQueryPort {

	/**
	 * Minimal projection of the config the admission flow needs: its id,
	 * sales-window status and program name. Expands the original
	 * {@code (id, status)} shape with {@code programName} so the ficha
	 * (PDF / confirmation / payment-instructions emails) can display the
	 * program without {@code admission} importing {@code academic_config}'s
	 * program repository.
	 */
	Optional<AdmissionConfigInfo> findById(UUID id);

	/** {@code status} is the ticket-sales window (OPEN/CLOSED), never {@code selectionStatus}. */
	record AdmissionConfigInfo(UUID id, ProgramAdmissionConfigStatus status, String programName) {
	}
}