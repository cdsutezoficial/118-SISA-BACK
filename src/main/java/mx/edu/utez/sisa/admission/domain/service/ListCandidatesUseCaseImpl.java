package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.ListCandidatesUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.CallerDivisionScopePort;
import mx.edu.utez.sisa.admission.domain.port.out.CandidatePersonRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository.CandidateSearchCriteria;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository.CandidateSearchPage;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort.ProgramRef;
import mx.edu.utez.sisa.shared.model.Person;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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

	private final CandidatePersonRepository candidatePersonRepository;

	private final ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	private final CallerDivisionScopePort callerDivisionScopePort;

	public ListCandidatesUseCaseImpl(CandidateRepository candidateRepository,
			CandidatePersonRepository candidatePersonRepository,
			ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort,
			CallerDivisionScopePort callerDivisionScopePort) {
		this.candidateRepository = candidateRepository;
		this.candidatePersonRepository = candidatePersonRepository;
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

		Map<UUID, Person> personsById = loadPersons(result.content());
		Map<UUID, ProgramRef> programRefsByConfigId = loadProgramRefs(result.content());

		List<CandidateListItem> items = result.content().stream()
				.map(candidate -> toItem(candidate, personsById.get(candidate.getPersonId()),
						programRefsByConfigId.get(candidate.getAdmissionConfigId())))
				.toList();

		return new ListCandidatesResult(items, result.totalElements(), result.totalPages(), page, size);
	}

	private Map<UUID, Person> loadPersons(List<Candidate> candidates) {
		List<UUID> personIds = candidates.stream().map(Candidate::getPersonId).distinct().toList();
		if (personIds.isEmpty()) {
			return Map.of();
		}
		return candidatePersonRepository.findByIds(personIds).stream()
				.collect(Collectors.toMap(Person::getId, Function.identity()));
	}

	private Map<UUID, ProgramRef> loadProgramRefs(List<Candidate> candidates) {
		List<UUID> configIds = candidates.stream().map(Candidate::getAdmissionConfigId).distinct().toList();
		return programAdmissionConfigQueryPort.findProgramRefsByConfigIds(configIds);
	}

	private static CandidateListItem toItem(Candidate candidate, Person person, ProgramRef programRef) {
		return new CandidateListItem(candidate.getId(), candidate.getFolio(), fullName(person),
				person == null ? null : person.getCurp(), programRef == null ? null : programRef.programId(),
				programRef == null ? null : programRef.programName(), candidate.getStatus(),
				candidate.getRegisteredAt());
	}

	/**
	 * "Ana Torres Ramos" — a missing second surname must not leave a trailing
	 * space, so the joined parts are trimmed; mirrors
	 * {@code AccessFichaPaymentUseCaseImpl#fullName}.
	 */
	private static String fullName(Person person) {
		if (person == null) {
			return null;
		}
		String name = (join(person.getFirstName()) + join(person.getLastName1()) + join(person.getLastName2())).trim();
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
