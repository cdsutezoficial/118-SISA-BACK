package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.PeriodStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase.PeriodResult;

import java.util.UUID;

/**
 * Transitions an {@code AcademicPeriod}'s status, enforcing the PO-confirmed
 * (2026-07-20) strictly sequential, forward-only lifecycle
 * {@code CONFIGURATION -> ENROLLMENT -> ACTIVE -> CLOSED} (see
 * {@code AcademicPeriod#changeStatus} for the state machine). Single
 * interactor parameterized by target status, mirroring
 * {@code ChangeSubjectClassificationStatusUseCase} — but unlike that binary
 * toggle, an invalid target here throws
 * {@code InvalidPeriodStatusTransitionException} (400) instead of silently
 * succeeding.
 */
public interface ChangeAcademicPeriodStatusUseCase {

	PeriodResult changeStatus(ChangeStatusCommand command);

	/**
	 * @param callerId reserved for future audit-log attribution; not consulted in this slice —
	 *                 mirrors {@code ChangeSubjectClassificationStatusUseCase.ChangeStatusCommand}
	 * @param periodId the period whose status is transitioning
	 * @param target   the desired {@link PeriodStatus}
	 */
	record ChangeStatusCommand(UUID callerId, UUID periodId, PeriodStatus target) {
	}
}
