package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Paginated, filterable query for the admission module's candidate list
 * ("Candidatos" screen), mirroring {@code ListOutreachChannelsUseCase}'s
 * convention. The caller id is part of the query, not an afterthought: the
 * Director's division scope is resolved from it, so the same endpoint answers
 * "all candidates" or "my division's candidates" without the caller choosing.
 */
public interface ListCandidatesUseCase {

	ListCandidatesResult listCandidates(ListCandidatesQuery query);

	/**
	 * @param callerId  authenticated user; drives the server-side division scope
	 * @param status    optional — filters to this exact status
	 * @param programId optional — the candidate's chosen program
	 * @param periodId  optional — the chosen config's destination period
	 * @param search    optional free-text match against folio, CURP or name
	 * @param page      zero-based page index; negative values are normalized to 0
	 * @param size      page size; normalized to the default and capped at the max
	 */
	record ListCandidatesQuery(UUID callerId, CandidateStatus status, UUID programId, UUID periodId, String search,
			int page, int size) {

		public static final int DEFAULT_PAGE_SIZE = 20;

		public static final int MAX_PAGE_SIZE = 100;
	}

	record ListCandidatesResult(List<CandidateListItem> items, long totalElements, int totalPages, int page,
			int size) {
	}

	/**
	 * One row. {@code fullName}, {@code curp} and {@code programName} are nullable
	 * because a candidate's {@code Person} or its config's program can, in
	 * principle, be missing; the row is still listed so the anomaly is visible
	 * rather than silently hidden.
	 */
	record CandidateListItem(UUID id, String folio, String fullName, String curp, UUID programId,
			String programName, CandidateStatus status, Instant registeredAt) {
	}
}
