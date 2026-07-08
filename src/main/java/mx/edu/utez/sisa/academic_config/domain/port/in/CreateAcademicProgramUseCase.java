package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramStatus;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;

import java.util.UUID;

/**
 * Creates an {@code AcademicProgram} catalog entry (spec: "Create Academic
 * Program"). Role authorization (ADMIN or SERVICIOS_ESCOLARES) is enforced by
 * {@code SecurityFilterConfig}, not here — mirrors
 * {@code CreateAcademicDivisionUseCase}.
 */
public interface CreateAcademicProgramUseCase {

	AcademicProgramResult createProgram(CreateAcademicProgramCommand command);

	/**
	 * @param divisionId          required — MUST NOT be omitted or {@code null}; MUST reference an
	 *                            existing {@code AcademicDivision} (spec: "divisionId MUST be
	 *                            required", unlike {@code AcademicDivision.directorPersonId})
	 * @param code                MUST be unique across all programs
	 * @param offerName           combined with {@code modality} as the uniqueness scope — see
	 *                            {@link AcademicProgramResult}
	 * @param continuityProgramId optional — MAY be omitted or {@code null}; schema-only in this change,
	 *                            no validation or linking logic applied (spec: "continuityProgramId MAY
	 *                            be omitted or null at creation")
	 */
	record CreateAcademicProgramCommand(UUID divisionId, String name, String offerName, String code,
			AcademicLevel level, ProgramModality modality, UUID continuityProgramId, String description) {
	}

	/**
	 * Shared result shape reused by {@code UpdateAcademicProgramUseCase},
	 * {@code GetAcademicProgramUseCase}, and
	 * {@code ChangeAcademicProgramStatusUseCase} — all four return the full
	 * post-operation program state (design.md — Decision: single
	 * {@code AcademicProgramResult} reused by Create/Update/Get/ChangeStatus).
	 */
	record AcademicProgramResult(UUID id, UUID divisionId, String name, String offerName, String code,
			AcademicLevel level, ProgramModality modality, UUID continuityProgramId, String description,
			ProgramStatus status) {
	}
}
