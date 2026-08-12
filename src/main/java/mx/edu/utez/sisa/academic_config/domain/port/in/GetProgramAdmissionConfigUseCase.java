package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.OpenProgramAdmissionUseCase.ProgramAdmissionConfigResult;

import java.util.UUID;

/**
 * Fetches a single {@code ProgramAdmissionConfig} by id. No special get-by-id
 * business rules beyond standard 404-if-missing — same convention as
 * {@code GetGenerationUseCase}. Reuses {@link ProgramAdmissionConfigResult},
 * same shape as Create.
 */
public interface GetProgramAdmissionConfigUseCase {

	ProgramAdmissionConfigResult getById(UUID id);
}
