package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.CreateSubjectClassificationUseCase.ClassificationResult;

import java.util.UUID;

/**
 * Updates an existing {@code SubjectClassification}'s catalog fields (docs:
 * {@code 02-config-academica.md} lines 15-24 — Phase 4 "Actualización").
 * {@code code} uniqueness is revalidated, EXCLUDING the record's own current
 * row — same self-update rule as {@code UpdateAcademicDivisionUseCase}.
 * {@code name} is still deliberately NOT unique (unchanged from Create).
 * {@code status} is deliberately absent from this command — status
 * transitions are the sole responsibility of the future
 * {@code ChangeSubjectClassificationStatusUseCase} (Phase 5).
 */
public interface UpdateSubjectClassificationUseCase {

	ClassificationResult updateClassification(UpdateClassificationCommand command);

	record UpdateClassificationCommand(UUID classificationId, String name, String code) {
	}
}
