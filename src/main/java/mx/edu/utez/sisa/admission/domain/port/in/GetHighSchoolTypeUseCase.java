package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase.HighSchoolTypeResult;

import java.util.UUID;

/**
 * Fetches a single {@code HighSchoolType} by id. No special get-by-id
 * business rules beyond standard 404-if-missing. Reuses
 * {@link HighSchoolTypeResult} — same convention as
 * {@code GetOutreachChannelUseCase} reusing {@code OutreachChannelResult}.
 */
public interface GetHighSchoolTypeUseCase {

	HighSchoolTypeResult getById(UUID id);
}
