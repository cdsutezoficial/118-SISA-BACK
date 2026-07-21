package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.GenerationStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGenerationUseCase.GenerationResult;

import java.util.UUID;

/**
 * Toggles a {@code Generation}'s status between {@code ACTIVE} and
 * {@code FINISHED} — a simple 2-state toggle (NOT a sequential state machine
 * like {@code ChangeAcademicPeriodStatusUseCase}'s 4-state lifecycle), same
 * shape as {@code ChangeSubjectClassificationStatusUseCase}. Single
 * interactor parameterized by target status; both directions are always
 * valid.
 */
public interface ChangeGenerationStatusUseCase {

	GenerationResult changeStatus(ChangeStatusCommand command);

	/**
	 * @param callerId     reserved for future audit-log attribution; not consulted in this slice —
	 *                     mirrors {@code ChangeAcademicPeriodStatusUseCase.ChangeStatusCommand}
	 * @param generationId the generation whose status is transitioning
	 * @param target       the desired {@link GenerationStatus} — {@code ACTIVE} or {@code FINISHED}
	 */
	record ChangeStatusCommand(UUID callerId, UUID generationId, GenerationStatus target) {
	}
}
