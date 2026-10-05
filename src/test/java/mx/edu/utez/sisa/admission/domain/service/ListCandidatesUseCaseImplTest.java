package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.ListCandidatesUseCase.CandidateListItem;
import mx.edu.utez.sisa.admission.domain.port.in.ListCandidatesUseCase.ListCandidatesQuery;
import mx.edu.utez.sisa.admission.domain.port.in.ListCandidatesUseCase.ListCandidatesResult;
import mx.edu.utez.sisa.admission.domain.port.out.CallerDivisionScopePort;
import mx.edu.utez.sisa.admission.domain.port.out.CallerDivisionScopePort.DivisionScope;
import mx.edu.utez.sisa.admission.domain.port.out.CandidatePersonRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository.CandidateSearchCriteria;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository.CandidateSearchPage;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort.ProgramRef;
import mx.edu.utez.sisa.shared.model.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListCandidatesUseCaseImplTest {

	private static final UUID CALLER = UUID.randomUUID();

	private static final UUID DIVISION = UUID.randomUUID();

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private CandidatePersonRepository candidatePersonRepository;

	@Mock
	private ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	@Mock
	private CallerDivisionScopePort callerDivisionScopePort;

	private ListCandidatesUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ListCandidatesUseCaseImpl(candidateRepository, candidatePersonRepository,
				programAdmissionConfigQueryPort, callerDivisionScopePort);
	}

	private static Candidate candidateWithId(UUID id, UUID personId, UUID admissionConfigId, String folio) {
		Candidate candidate = new Candidate(personId, admissionConfigId, folio, true, true, null);
		ReflectionTestUtils.setField(candidate, "id", id);
		return candidate;
	}

	private static Person personWithId(UUID id, String curp, String firstName, String lastName1, String lastName2) {
		Person person = new Person(curp, firstName, lastName1, lastName2, null);
		ReflectionTestUtils.setField(person, "id", id);
		return person;
	}

	private static ListCandidatesQuery query(int page, int size) {
		return new ListCandidatesQuery(CALLER, CandidateStatus.REGISTERED, null, null, null, page, size);
	}

	@Test
	void unrestrictedCallerIsNotFilteredByDivision() {
		when(callerDivisionScopePort.resolve(CALLER)).thenReturn(DivisionScope.unrestricted());
		when(candidateRepository.search(any())).thenReturn(new CandidateSearchPage(List.of(), 0, 0));

		useCase.listCandidates(query(0, 20));

		ArgumentCaptor<CandidateSearchCriteria> captor = ArgumentCaptor.forClass(CandidateSearchCriteria.class);
		verify(candidateRepository).search(captor.capture());
		assertThat(captor.getValue().divisionId()).isNull();
		assertThat(captor.getValue().status()).isEqualTo(CandidateStatus.REGISTERED);
	}

	@Test
	void directorIsForcedToItsOwnDivision() {
		when(callerDivisionScopePort.resolve(CALLER)).thenReturn(DivisionScope.director(DIVISION));
		when(candidateRepository.search(any())).thenReturn(new CandidateSearchPage(List.of(), 0, 0));

		useCase.listCandidates(query(0, 20));

		ArgumentCaptor<CandidateSearchCriteria> captor = ArgumentCaptor.forClass(CandidateSearchCriteria.class);
		verify(candidateRepository).search(captor.capture());
		assertThat(captor.getValue().divisionId()).isEqualTo(DIVISION);
	}

	@Test
	void directorWithoutDivisionSeesNothingAndNeverQueries() {
		when(callerDivisionScopePort.resolve(CALLER)).thenReturn(DivisionScope.director(null));

		ListCandidatesResult result = useCase.listCandidates(query(0, 20));

		assertThat(result.items()).isEmpty();
		assertThat(result.totalElements()).isZero();
		verify(candidateRepository, never()).search(any());
	}

	@Test
	void mapsPersonAndProgramNameOntoTheRow() {
		UUID personId = UUID.randomUUID();
		UUID configId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		UUID candidateId = UUID.randomUUID();
		Candidate candidate = candidateWithId(candidateId, personId, configId, "ADM-2026-000001");
		Person person = personWithId(personId, "GOCD050101HDFRNS04", "Ana", "Torres", "Ramos");

		when(callerDivisionScopePort.resolve(CALLER)).thenReturn(DivisionScope.unrestricted());
		when(candidateRepository.search(any()))
				.thenReturn(new CandidateSearchPage(List.of(candidate), 1, 1));
		when(candidatePersonRepository.findByIds(List.of(personId))).thenReturn(List.of(person));
		when(programAdmissionConfigQueryPort.findProgramRefsByConfigIds(List.of(configId)))
				.thenReturn(Map.of(configId, new ProgramRef(programId, "Ing. en Tecnologías de la Información")));

		ListCandidatesResult result = useCase.listCandidates(query(0, 20));

		assertThat(result.items()).hasSize(1);
		CandidateListItem item = result.items().get(0);
		assertThat(item.id()).isEqualTo(candidateId);
		assertThat(item.folio()).isEqualTo("ADM-2026-000001");
		assertThat(item.fullName()).isEqualTo("Ana Torres Ramos");
		assertThat(item.curp()).isEqualTo("GOCD050101HDFRNS04");
		assertThat(item.programId()).isEqualTo(programId);
		assertThat(item.programName()).isEqualTo("Ing. en Tecnologías de la Información");
		assertThat(item.status()).isEqualTo(CandidateStatus.REGISTERED);
	}

	@Test
	void missingPersonLeavesNameAndCurpNullWithoutDroppingTheRow() {
		UUID personId = UUID.randomUUID();
		UUID configId = UUID.randomUUID();
		Candidate candidate = candidateWithId(UUID.randomUUID(), personId, configId, "ADM-2026-000002");

		when(callerDivisionScopePort.resolve(CALLER)).thenReturn(DivisionScope.unrestricted());
		when(candidateRepository.search(any()))
				.thenReturn(new CandidateSearchPage(List.of(candidate), 1, 1));
		when(candidatePersonRepository.findByIds(List.of(personId))).thenReturn(List.of());
		when(programAdmissionConfigQueryPort.findProgramRefsByConfigIds(List.of(configId))).thenReturn(Map.of());

		ListCandidatesResult result = useCase.listCandidates(query(0, 20));

		assertThat(result.items()).hasSize(1);
		assertThat(result.items().get(0).fullName()).isNull();
		assertThat(result.items().get(0).curp()).isNull();
		assertThat(result.items().get(0).programId()).isNull();
		assertThat(result.items().get(0).programName()).isNull();
	}

	@Test
	void nonPositiveSizeFallsBackToDefault() {
		when(callerDivisionScopePort.resolve(CALLER)).thenReturn(DivisionScope.unrestricted());
		when(candidateRepository.search(any())).thenReturn(new CandidateSearchPage(List.of(), 0, 0));

		useCase.listCandidates(query(0, 0));

		ArgumentCaptor<CandidateSearchCriteria> captor = ArgumentCaptor.forClass(CandidateSearchCriteria.class);
		verify(candidateRepository).search(captor.capture());
		assertThat(captor.getValue().size()).isEqualTo(ListCandidatesQuery.DEFAULT_PAGE_SIZE);
	}

	@Test
	void oversizedSizeIsCappedAtMax() {
		when(callerDivisionScopePort.resolve(CALLER)).thenReturn(DivisionScope.unrestricted());
		when(candidateRepository.search(any())).thenReturn(new CandidateSearchPage(List.of(), 0, 0));

		useCase.listCandidates(query(0, 500));

		ArgumentCaptor<CandidateSearchCriteria> captor = ArgumentCaptor.forClass(CandidateSearchCriteria.class);
		verify(candidateRepository).search(captor.capture());
		assertThat(captor.getValue().size()).isEqualTo(ListCandidatesQuery.MAX_PAGE_SIZE);
	}

	@Test
	void negativePageIsNormalizedToZero() {
		when(callerDivisionScopePort.resolve(CALLER)).thenReturn(DivisionScope.unrestricted());
		when(candidateRepository.search(any())).thenReturn(new CandidateSearchPage(List.of(), 0, 0));

		useCase.listCandidates(query(-3, 20));

		ArgumentCaptor<CandidateSearchCriteria> captor = ArgumentCaptor.forClass(CandidateSearchCriteria.class);
		verify(candidateRepository).search(captor.capture());
		assertThat(captor.getValue().page()).isZero();
	}

	@Test
	void blankSearchIsNormalizedToNull() {
		when(callerDivisionScopePort.resolve(CALLER)).thenReturn(DivisionScope.unrestricted());
		when(candidateRepository.search(any())).thenReturn(new CandidateSearchPage(List.of(), 0, 0));

		useCase.listCandidates(new ListCandidatesQuery(CALLER, null, null, null, "   ", 0, 20));

		ArgumentCaptor<CandidateSearchCriteria> captor = ArgumentCaptor.forClass(CandidateSearchCriteria.class);
		verify(candidateRepository).search(captor.capture());
		assertThat(captor.getValue().search()).isNull();
	}
}
