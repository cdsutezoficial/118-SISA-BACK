package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase.AcademicProgramResult;

import java.util.UUID;

/**
 * Fetches a single {@code AcademicProgram} by id (spec: "Get Academic
 * Program by Id"). Reuses {@link AcademicProgramResult} — same shape as
 * Create/Update/ChangeStatus.
 */
public interface GetAcademicProgramUseCase {

	AcademicProgramResult getById(UUID id);
}
