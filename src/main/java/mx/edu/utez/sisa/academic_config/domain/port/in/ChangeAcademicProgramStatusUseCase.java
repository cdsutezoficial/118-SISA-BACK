package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase.AcademicProgramResult;

import java.util.UUID;

/**
 * Toggles an {@code AcademicProgram}'s status between {@code ACTIVE} and
 * {@code INACTIVE} (spec: "Activate and Deactivate Academic Program").
 * Single interactor parameterized by target status, mirroring
 * {@code ChangeAcademicDivisionStatusUseCase} — fulfills both the spec's
 * Activate and Deactivate scenarios.
 */
public interface ChangeAcademicProgramStatusUseCase {

	AcademicProgramResult changeStatus(ChangeStatusCommand command);

	/**
	 * @param callerId  reserved for future audit-log attribution; not consulted in this slice — mirrors
	 *                  {@code ChangeAcademicDivisionStatusUseCase.ChangeStatusCommand}
	 * @param programId the program whose status is transitioning
	 * @param target    the desired {@link ProgramStatus} — {@code ACTIVE} or {@code INACTIVE}
	 */
	record ChangeStatusCommand(UUID callerId, UUID programId, ProgramStatus target) {
	}
}
