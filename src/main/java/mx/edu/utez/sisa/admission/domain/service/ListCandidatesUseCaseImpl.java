package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.ListCandidatesUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.CallerDivisionScopePort;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository.CandidateSearchCriteria;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository.CandidateSearchPage;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort.ProgramRef;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Candidate list for the "Candidatos" screen. Two rules shape it:
 *
 * <ul>
 * <li><strong>Division scope is server-side.</strong> The caller's
 * {@code DIRECTOR_DIVISION} grant is resolved from the JWT id and forced onto
 * the query, so a Director cannot widen their view by editing the URL. A
 * Director whose grant carries no division is scoped to nothing (fail closed) —
 * an empty page, never the whole institution.</li>
 * <li><strong>One query per concern, not per row.</strong> Person names/CURPs
 * and program names are batch-loaded for the whole page, so a 100-row page stays
 * three queries (search, persons, program names) instead of up to 200.</li>
 * </ul>
 */
public class ListCandidatesUseCaseImpl implements ListCandidatesUseCase {

	private final CandidateRepository candidateRepository;

	private final ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	private final CallerDivisionScopePort callerDivisionScopePort;

	public ListCandidatesUseCaseImpl(CandidateRepository candidateRepository,
			ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort,
			CallerDivisionScopePort callerDivisionScopePort) {
		this.candidateRepository = candidateRepository;
		this.programAdmissionConfigQueryPort = programAdmissionConfigQueryPort;
		this.callerDivisionScopePort = callerDivisionScopePort;
	}

	@Override
	public ListCandidatesResult listCandidates(ListCandidatesQuery query) {
		int page = Math.max(query.page(), 0);
		int size = normalizeSize(query.size());

		CallerDivisionScopePort.DivisionScope scope = callerDivisionScopePort.resolve(query.callerId());
		if (scope.divisionScoped() && scope.divisionId() == null) {
			// Fail closed: a Director without a division sees nothing.
			return new ListCandidatesResult(List.of(), 0, 0, page, size);
		}
		UUID divisionId = scope.divisionScoped() ? scope.divisionId() : null;

		CandidateSearchCriteria criteria = new CandidateSearchCriteria(query.status(), query.programId(),
				query.periodId(), divisionId, normalizeSearch(query.search()), page, size);
		CandidateSearchPage result = candidateRepository.search(criteria);

		Map<UUID, ProgramRef> programRefsByConfigId = loadProgramRefs(result.content());

		List<CandidateListItem> items = result.content().stream()
				.map(candidate -> toItem(candidate, programRefsByConfigId.get(candidate.getAdmissionConfigId())))
				.toList();

		return new ListCandidatesResult(items, result.totalElements(), result.totalPages(), page, size);
	}

	private Map<UUID, ProgramRef> loadProgramRefs(List<Candidate> candidates) {
		List<UUID> configIds = candidates.stream().map(Candidate::getAdmissionConfigId).distinct().toList();
		return programAdmissionConfigQueryPort.findProgramRefsByConfigIds(configIds);
	}

	private static CandidateListItem toItem(Candidate candidate, ProgramRef programRef) {
		return new CandidateListItem(candidate.getId(), candidate.getFolio(), fullName(candidate),
				candidate.getCurp(), programRef == null ? null : programRef.programId(),
				programRef == null ? null : programRef.programName(), candidate.getStatus(),
				candidate.getRegisteredAt());
	}

	/**
	 * "Ana Torres Ramos" — a missing second surname must not leave a trailing
	 * space, so the joined parts are trimmed; mirrors
	 * {@code AccessFichaPaymentUseCaseImpl#fullName}.
	 */
	private static String fullName(Candidate candidate) {
		String name = (join(candidate.getFirstName()) + join(candidate.getLastName1()) + join(candidate.getLastName2()))
				.trim();
		return name.isEmpty() ? null : name;
	}

	private static String join(String value) {
		return value == null || value.isBlank() ? "" : value.trim() + " ";
	}

	private static String normalizeSearch(String search) {
		return search == null || search.isBlank() ? null : search.trim();
	}

	private static int normalizeSize(int size) {
		if (size <= 0) {
			return ListCandidatesQuery.DEFAULT_PAGE_SIZE;
		}
		return Math.min(size, ListCandidatesQuery.MAX_PAGE_SIZE);
	}
}
