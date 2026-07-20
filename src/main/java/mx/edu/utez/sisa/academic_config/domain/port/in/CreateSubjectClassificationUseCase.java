package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.ClassificationStatus;

import java.util.UUID;

/**
 * Creates a {@code SubjectClassification} catalog entry (docs:
 * {@code 02-config-academica.md} lines 15-24 — Phase 2 "Registro"). Role
 * authorization (ADMIN or SERVICIOS_ESCOLARES) is enforced by
 * {@code SecurityFilterConfig}, not here — same convention as
 * {@code CreateAcademicDivisionUseCase}.
 */
public interface CreateSubjectClassificationUseCase {

	ClassificationResult createClassification(CreateClassificationCommand command);

	/**
	 * @param name deliberately NOT unique (unlike {@code AcademicDivision.name}) — see {@code SubjectClassification}'s javadoc
	 * @param code MUST be unique across all classifications
	 */
	record CreateClassificationCommand(String name, String code) {
	}

	record ClassificationResult(UUID id, String name, String code, ClassificationStatus status) {
	}
}
