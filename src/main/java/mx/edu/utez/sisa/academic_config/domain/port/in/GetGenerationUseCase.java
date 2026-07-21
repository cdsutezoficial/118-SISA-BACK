package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGenerationUseCase.GenerationResult;

import java.util.UUID;

/**
 * Fetches a single {@code Generation} by id. No special get-by-id business
 * rules beyond standard 404-if-missing — same convention as
 * {@code GetAcademicPeriodUseCase}. Reuses {@link GenerationResult}, same
 * shape as Create.
 */
public interface GetGenerationUseCase {

	GenerationResult getById(UUID id);
}
