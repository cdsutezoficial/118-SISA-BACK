package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase.PeriodResult;

import java.util.UUID;

/**
 * Fetches a single {@code AcademicPeriod} by id. No special get-by-id
 * business rules beyond standard 404-if-missing — same convention as
 * {@code GetSubjectClassificationUseCase}. Reuses {@link PeriodResult}, same
 * shape as Create.
 */
public interface GetAcademicPeriodUseCase {

	PeriodResult getById(UUID id);
}
