package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase.HighSchoolTypeResult;

import java.util.UUID;

/**
 * Updates an existing {@code HighSchoolType}'s {@code name}. {@code status}
 * is deliberately absent — status transitions are the sole responsibility of
 * {@code ChangeHighSchoolTypeStatusUseCase}.
 *
 * <p>The {@code name} is revalidated for uniqueness by the implementation (Fase
 * 10), excluding the row being edited so saving an unchanged entry is not a
 * conflict — the same self-exclusion {@code UpdateAcademicDivisionUseCase} does.
 */
public interface UpdateHighSchoolTypeUseCase {

	HighSchoolTypeResult updateHighSchoolType(UpdateHighSchoolTypeCommand command);

	record UpdateHighSchoolTypeCommand(UUID highSchoolTypeId, String name) {
	}
}
