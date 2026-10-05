package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A single row of {@code GET /candidates}:
 * {@code {id, folio, fullName, curp, programId, programName, status, registeredAt}}.
 * Deliberately lean — the table renders exactly these columns, and anything the
 * screen does not show (payments, induction flags, address…) belongs to
 * {@code GET /candidates/{id}} instead of leaking onto the list.
 */
public record CandidateListItemResponse(UUID id, String folio, String fullName, String curp, UUID programId,
		String programName, CandidateStatus status, Instant registeredAt) {
}
