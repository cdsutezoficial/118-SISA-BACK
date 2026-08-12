package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase.HighSchoolTypeResult;

import java.util.UUID;

/**
 * Updates an existing {@code HighSchoolType}'s {@code name}. {@code status}
 * is deliberately absent — status transitions are the sole responsibility of
 * {@code ChangeHighSchoolTypeStatusUseCase}. No uniqueness to revalidate,
 * same convention as {@code UpdateOutreachChannelUseCase}.
 */
public interface UpdateHighSchoolTypeUseCase {

	HighSchoolTypeResult updateHighSchoolType(UpdateHighSchoolTypeCommand command);

	record UpdateHighSchoolTypeCommand(UUID highSchoolTypeId, String name) {
	}
}
