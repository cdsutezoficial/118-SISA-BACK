package mx.edu.utez.sisa.admission.domain.port.out;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for the {@link Candidate} aggregate root. The
 * applicant's {@code Person} is persisted through a separate port
 * ({@code CandidatePersonRepository}) since {@code Person} is a shared-kernel
 * aggregate whose lifecycle reaches across bounded contexts.
 */
public interface CandidateRepository {

	Candidate save(Candidate candidate);

	Optional<Candidate> findById(UUID id);

	/**
	 * Backs {@code RegisterCandidateUseCase}'s folio generation
	 * ({@code ADM-{year}-{seq}:06d} â€” format inherited from the frontend's
	 * mock ficha). {@code prefix} is {@code "ADM-{year}-"}; the sequence is
	 * derived from the current count, so folios are unique per calendar year
	 * (the v1 approximation of the domain's "Ãºnico por periodo"; the period
	 * of the chosen {@code ProgramAdmissionConfig} is available to a future
	 * exact-per-period implementation).
	 */
	long countByFolioStartingWith(String prefix);

	/**
	 * Exact folio lookup for the "vuelve a pagar mi ficha" entry point
	 * ({@code AccessFichaPaymentUseCase}), which identifies the candidate by
	 * folio and then cross-checks the CURP suffix. Case-insensitive on the
	 * caller's side: the use case normalizes to upper case before calling, so
	 * this match is exact against the stored folio.
	 */
	Optional<Candidate> findByFolio(String folio);

	/**
	 * Every candidate in the given status, backing the daily ficha-expiry sweep
	 * ({@code ExpireStaleFichaPaymentsUseCase}) â€” it loads the {@code REGISTERED}
	 * fichas and expires the ones past their payment window. Not scoped by
	 * config or period on purpose: a ficha expires on its own private clock,
	 * regardless of where the admission process stands.
	 */
	List<Candidate> findAllByStatus(CandidateStatus status);

	/**
	 * Every ficha ever registered for a person, whatever its status. Backs the
	 * CURP re-registration lock (Â§1.10): the lock is on the <em>ficha</em>, not
	 * on the person, so registration reads the person's fichas and decides on
	 * their statuses instead of refusing on the person's mere existence.
	 */
	List<Candidate> findAllByPersonId(UUID personId);

	/**
	 * Filterable, paginated query backing {@code ListCandidatesUseCase}.
	 *
	 * <p>{@code divisionId} is the server-side scope enforced for
	 * {@code DIRECTOR_DIVISION} callers (RN-ADM-004); a {@code null} value means
	 * "every division" and is only ever passed for callers the use case has
	 * already cleared as unrestricted. The adapter must NOT silently treat it as
	 * an unset filter when the caller <em>is</em> division-scoped â€” that decision
	 * belongs to the use case, which returns an empty page instead of everybody.
	 */
	CandidateSearchPage search(CandidateSearchCriteria criteria);

	/**
	 * @param status     optional â€” filters to candidates in this exact status
	 * @param programId  optional â€” the candidate's chosen config must point at
	 *                   this program
	 * @param periodId   optional â€” the candidate's chosen config must point at
	 *                   this destination period
	 * @param divisionId optional â€” the candidate's program must belong to this
	 *                   division (the Director scope)
	 * @param search     optional free-text match against folio, CURP or full name
	 * @param page       zero-based page index
	 * @param size       page size
	 */
	record CandidateSearchCriteria(CandidateStatus status, UUID programId, UUID periodId, UUID divisionId,
			String search, int page, int size) {
	}

	/**
	 * @param content       the {@link Candidate} rows for the requested page
	 * @param totalElements total matching rows across all pages
	 * @param totalPages    total page count for {@code totalElements} at the requested page size
	 */
	record CandidateSearchPage(List<Candidate> content, long totalElements, int totalPages) {
	}
}