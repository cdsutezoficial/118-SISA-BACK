package mx.edu.utez.sisa.admission.domain.port.out;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.shared.model.ProgramModality;

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
	 * sales-window status, program name/modality and the destination-period
	 * name. Expands the original {@code (id, status, programName)} shape so
	 * the ficha (PDF / confirmation / payment-instructions emails) can display
	 * the program and period without {@code admission} importing
	 * {@code academic_config}'s program/period repositories.
	 */
	Optional<AdmissionConfigInfo> findById(UUID id);

	/**
	 * @param modality   the chosen program's delivery modality ({@code PRESENCIAL}/{@code MIXTA}),
	 *                   resolved from the {@code AcademicProgram} — the admission flow never stores
	 *                   modality on {@code Candidate} itself (derived from the program).
	 * @param periodName the destination-period name the accepted candidates enroll into.
	 */
	record AdmissionConfigInfo(UUID id, ProgramAdmissionConfigStatus status, String programName,
			ProgramModality modality, String periodName) {
	}
}