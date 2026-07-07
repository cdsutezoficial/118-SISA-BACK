package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.DivisionStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase.AcademicDivisionResult;

import java.util.UUID;

/**
 * Toggles an {@code AcademicDivision}'s status between {@code ACTIVE} and
 * {@code INACTIVE} (spec: "Activate and Deactivate Academic Division").
 * Single interactor parameterized by target status (design.md — Decision:
 * Single status-change use case + idempotent PATCH) — fulfills both the
 * proposal's Activate and Deactivate capabilities.
 */
public interface ChangeAcademicDivisionStatusUseCase {

	AcademicDivisionResult changeStatus(ChangeStatusCommand command);

	/**
	 * @param callerId   reserved for future audit-log attribution; not consulted in this slice (no audit
	 *                   sink exists yet, and this module has no caller/User concept of its own)
	 * @param divisionId the division whose status is transitioning
	 * @param target     the desired {@link DivisionStatus} — {@code ACTIVE} or {@code INACTIVE}
	 */
	record ChangeStatusCommand(UUID callerId, UUID divisionId, DivisionStatus target) {
	}
}
