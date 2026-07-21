package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGenerationUseCase.GenerationResult;

import java.util.UUID;

/**
 * Updates an existing {@code Generation}'s FK associations and {@code number}
 * (PUT {@code /generations/{id}}), recomputing {@code code} if
 * {@code startPeriodId}/{@code number} changed. {@code (programId, number)}
 * uniqueness is revalidated, EXCLUDING the record's own current row — same
 * self-update rule as {@code UpdateAcademicPeriodUseCase}'s
 * {@code (year, periodNumber)} revalidation. {@code status} is deliberately
 * absent — status transitions are the sole responsibility of
 * {@code ChangeGenerationStatusUseCase}.
 */
public interface UpdateGenerationUseCase {

	GenerationResult updateGeneration(UpdateGenerationCommand command);

	record UpdateGenerationCommand(UUID generationId, UUID planId, UUID startPeriodId, int number) {
	}
}
