package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupUseCase.GroupResult;

import java.util.UUID;

/**
 * Fetches a single {@code Group} by id. No special get-by-id business rules
 * beyond standard 404-if-missing — same convention as
 * {@code GetGenerationUseCase}. Reuses {@link GroupResult}, same shape as
 * Create.
 */
public interface GetGroupUseCase {

	GroupResult getById(UUID id);
}
