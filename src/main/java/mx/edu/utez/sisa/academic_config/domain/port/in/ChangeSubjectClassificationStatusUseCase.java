package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.ClassificationStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateSubjectClassificationUseCase.ClassificationResult;

import java.util.UUID;

/**
 * Toggles a {@code SubjectClassification}'s status between {@code ACTIVE}
 * and {@code INACTIVE} (Phase 5 — ChangeStatus, the last phase of
 * {@code docs/plans/2026-07-15-subject-classification-crud.md}). Single
 * interactor parameterized by target status, mirroring
 * {@code ChangeAcademicDivisionStatusUseCase}/{@code ChangeAcademicProgramStatusUseCase}.
 */
public interface ChangeSubjectClassificationStatusUseCase {

	ClassificationResult changeStatus(ChangeStatusCommand command);

	/**
	 * @param callerId       reserved for future audit-log attribution; not consulted in this slice —
	 *                       mirrors {@code ChangeAcademicDivisionStatusUseCase.ChangeStatusCommand}
	 * @param classificationId the classification whose status is transitioning
	 * @param target         the desired {@link ClassificationStatus} — {@code ACTIVE} or {@code INACTIVE}
	 */
	record ChangeStatusCommand(UUID callerId, UUID classificationId, ClassificationStatus target) {
	}
}
