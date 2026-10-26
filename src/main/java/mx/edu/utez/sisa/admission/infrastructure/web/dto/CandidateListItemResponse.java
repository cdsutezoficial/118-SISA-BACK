package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A single row of {@code GET /candidates}:
 * {@code {id, personId, folio, fullName, curp, programId, programName, status,
 * registeredAt}}. Deliberately lean — the table renders exactly these columns,
 * and anything the screen does not show (payments, induction flags, address…)
 * belongs to {@code GET /candidates/{id}} instead of leaking onto the list.
 *
 * <p>{@code personId} is the shared person behind the candidate, exposed so the
 * discount screen can grant a discount (a person-scoped concept) straight from a
 * searched row.
 */
public record CandidateListItemResponse(UUID id, UUID personId, String folio, String fullName, String curp,
		UUID programId, String programName, CandidateStatus status, Instant registeredAt) {
}
