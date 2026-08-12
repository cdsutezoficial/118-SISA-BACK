package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.port.in.CreateOutreachChannelUseCase.OutreachChannelResult;

import java.util.UUID;

/**
 * Fetches a single {@code OutreachChannel} by id. No special get-by-id
 * business rules beyond standard 404-if-missing (no FK relationships
 * reference this catalog yet — {@code Candidate} is out of scope). Reuses
 * {@link OutreachChannelResult} — same convention as
 * {@code GetSubjectClassificationUseCase} reusing {@code ClassificationResult}.
 */
public interface GetOutreachChannelUseCase {

	OutreachChannelResult getById(UUID id);
}
