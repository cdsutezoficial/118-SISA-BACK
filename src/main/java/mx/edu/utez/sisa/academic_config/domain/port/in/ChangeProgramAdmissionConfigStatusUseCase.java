package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.OpenProgramAdmissionUseCase.ProgramAdmissionConfigResult;

import java.util.UUID;

/**
 * Toggles a {@code ProgramAdmissionConfig}'s {@code status} between
 * {@code OPEN} and {@code CLOSED} ONLY — a simple 2-state toggle (NOT a
 * sequential state machine), same shape as
 * {@code ChangeGenerationStatusUseCase}. Single interactor parameterized by
 * target status; both directions are always valid. NEVER touches
 * {@code selectionStatus} — that field has no use case to change it in this
 * phase (plan §3/§9).
 */
public interface ChangeProgramAdmissionConfigStatusUseCase {

	ProgramAdmissionConfigResult changeStatus(ChangeStatusCommand command);

	/**
	 * @param callerId reserved for future audit-log attribution; not consulted in this slice —
	 *                 mirrors {@code ChangeGenerationStatusUseCase.ChangeStatusCommand}
	 * @param configId the config whose status is transitioning
	 * @param target   the desired {@link ProgramAdmissionConfigStatus} — {@code OPEN} or
	 *                 {@code CLOSED}
	 */
	record ChangeStatusCommand(UUID callerId, UUID configId, ProgramAdmissionConfigStatus target) {
	}
}
