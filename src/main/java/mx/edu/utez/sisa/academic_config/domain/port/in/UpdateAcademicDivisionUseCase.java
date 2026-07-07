package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase.AcademicDivisionResult;

import java.util.UUID;

/**
 * Updates an existing {@code AcademicDivision}'s catalog fields (spec:
 * "Update Academic Division"). The same uniqueness and director-existence
 * rules from {@code CreateAcademicDivisionUseCase} apply, except a
 * division's own current {@code name}/{@code code} never counts as a
 * conflict against itself. {@code status} is deliberately absent from this
 * command — status transitions are the sole responsibility of
 * {@code ChangeAcademicDivisionStatusUseCase}.
 */
public interface UpdateAcademicDivisionUseCase {

	AcademicDivisionResult updateDivision(UpdateAcademicDivisionCommand command);

	record UpdateAcademicDivisionCommand(UUID divisionId, String name, String code, String description,
			UUID directorPersonId) {
	}
}
