package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase.AcademicProgramResult;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;

import java.util.UUID;

/**
 * Updates an existing {@code AcademicProgram}'s catalog fields (spec:
 * "Update Academic Program"). The same {@code divisionId}-existence and dual
 * uniqueness rules ({@code code}; {@code (offerName, modality)}) from
 * {@code CreateAcademicProgramUseCase} apply, except a program's own current
 * values never count as a conflict against itself. {@code status} is
 * deliberately absent from this command — status transitions are the sole
 * responsibility of {@code ChangeAcademicProgramStatusUseCase}.
 */
public interface UpdateAcademicProgramUseCase {

	AcademicProgramResult updateProgram(UpdateAcademicProgramCommand command);

	record UpdateAcademicProgramCommand(UUID programId, UUID divisionId, String name, String offerName, String code,
			AcademicLevel level, ProgramModality modality, UUID continuityProgramId, String description) {
	}
}
