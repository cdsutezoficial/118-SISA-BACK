package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.CreateSubjectClassificationUseCase.ClassificationResult;

import java.util.UUID;

/**
 * Fetches a single {@code SubjectClassification} by id (docs:
 * {@code 02-config-academica.md} lines 15-24 — Phase 3 "Detalle"). No
 * special get-by-id business rules beyond standard 404-if-missing (no FK
 * relationships reference this catalog yet). Reuses
 * {@link ClassificationResult} — same shape as Create, same convention as
 * {@code GetAcademicDivisionUseCase} reusing {@code AcademicDivisionResult}.
 */
public interface GetSubjectClassificationUseCase {

	ClassificationResult getById(UUID id);
}
