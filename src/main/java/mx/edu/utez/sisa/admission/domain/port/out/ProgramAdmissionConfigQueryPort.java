package mx.edu.utez.sisa.admission.domain.port.out;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.shared.model.ProgramModality;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only out-port over {@code ProgramAdmissionConfig} (a bounded context
 * NOT owned by admission — it lives in {@code academic_config}).
 * {@code admission} validates the applicant's chosen program window by
 * looking up the config this way rather than importing
 * {@code academic_config}'s repository port directly (same "own minimal
 * access" rationale as {@code CandidatePersonRepository} / academic_config's
 * {@code PersonLookupJpaRepository}).
 *
 * <p>The cap is not resolved here: {@code maxCandidates} comes across on the
 * projection, but the paid-ficha count it is compared against belongs to
 * {@code admission}'s own tables, so the use case asks
 * {@code AdmissionPaymentRepository} for it. The rule reads "PAID fichas <
 * maxCandidates" and each side of that comparison is owned by the context that
 * owns its data.
 */
public interface ProgramAdmissionConfigQueryPort {

	/**
	 * Minimal projection of the config the admission flow needs: its id,
	 * sales-window status, program id/name/modality, the destination-period
	 * name and the three fields that actually gate a registration — the
	 * {@code opensAt}/{@code closesAt} instants and {@code maxCandidates}.
	 * Expands the original {@code (id, status, programName)} shape so
	 * the ficha (PDF / confirmation / payment-instructions emails) can display
	 * the program and period without {@code admission} importing
	 * {@code academic_config}'s program/period repositories. {@code programId}
	 * powers the payment-concept resolution at registration (the ficha amount
	 * is the cost of the program's {@code ADMISSION} concept, Fase 11).
	 */
	Optional<AdmissionConfigInfo> findById(UUID id);

	/**
	 * Batch lookup of {@code admissionConfigId → (programId, programName)}
	 * backing the candidate list, which must not issue one config/program query
	 * per row. Configs absent from the table — or whose {@code programId} is
	 * null — are simply missing from the returned map; the list still renders
	 * those rows, with a null program id/name rather than dropping the
	 * candidate.
	 */
	Map<UUID, ProgramRef> findProgramRefsByConfigIds(List<UUID> configIds);

	/**
	 * The program behind one admission config, as the candidate list needs it.
	 *
	 * <p>{@code programId} is the {@code AcademicProgram}'s id — deliberately
	 * NOT the config's own id — so the row's {@code programId} and the list's
	 * {@code programId} filter are the same value and can round-trip. Exposing
	 * the config id here would make the "Programa Solicitado" filter match
	 * nothing (the filter matches {@code cfg.programId = :programId}).
	 */
	record ProgramRef(UUID programId, String programName) {
	}

	/**
	 * @param programId the chosen program's id (also used to resolve the
	 *                  {@code ADMISSION} payment concept that prices the ficha)
	 * @param modality  the chosen program's delivery modality ({@code PRESENCIAL}/{@code MIXTA}),
	 *                  resolved from the {@code AcademicProgram} — the admission flow never stores
	 *                  modality on {@code Candidate} itself (derived from the program).
	 * @param periodName the destination-period name the accepted candidates enroll into.
	 * @param opensAt   when ticket sales open — registration outside the window is a 409.
	 * @param closesAt  when ticket sales close — registration outside the window is a 409.
	 * @param maxCandidates how many <em>paid</em> fichas the program may sell; the
	 *                      count behind it lives in {@code admission}.
	 */
	record AdmissionConfigInfo(UUID id, ProgramAdmissionConfigStatus status, UUID programId, String programName,
			String divisionName, ProgramModality modality, String periodName, Instant opensAt, Instant closesAt,
			int maxCandidates) {
	}
}