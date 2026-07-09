package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicProgramsUseCase.ListAcademicProgramsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicProgramsUseCase.ListAcademicProgramsResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicProgramsUseCase.ProgramSummary;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository.ProgramSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository.ProgramSearchPage;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListAcademicProgramsUseCaseImplTest {

	@Mock
	private AcademicProgramRepository programRepository;

	private ListAcademicProgramsUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ListAcademicProgramsUseCaseImpl(programRepository);
	}

	@Test
	void listPrograms_defaultPaginationUsesDefaultSize() {
		List<AcademicProgram> content = List.of(
				new AcademicProgram(UUID.randomUUID(), "Ingenieria en Software", "Ingenieria en Software", "ISC-01",
						AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, "desc", null),
				new AcademicProgram(UUID.randomUUID(), "Ingenieria Industrial", "Ingenieria Industrial", "ISC-02",
						AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, "desc", null));
		when(programRepository.search(any())).thenReturn(new ProgramSearchPage(content, 22, 2));

		ListAcademicProgramsResult result = useCase
				.listPrograms(new ListAcademicProgramsQuery(null, null, null, 0, 0));

		ArgumentCaptor<ProgramSearchCriteria> captor = ArgumentCaptor.forClass(ProgramSearchCriteria.class);
		verify(programRepository).search(captor.capture());
		assertThat(captor.getValue().size()).isEqualTo(ListAcademicProgramsQuery.DEFAULT_PAGE_SIZE);
		assertThat(captor.getValue().page()).isZero();
		assertThat(result.totalElements()).isEqualTo(22);
		assertThat(result.totalPages()).isEqualTo(2);
	}

	@Test
	void listPrograms_filtersByDivisionId() {
		UUID divisionId = UUID.randomUUID();
		when(programRepository.search(any())).thenReturn(new ProgramSearchPage(List.of(), 0, 0));

		useCase.listPrograms(new ListAcademicProgramsQuery(null, null, divisionId, 0, 20));

		ArgumentCaptor<ProgramSearchCriteria> captor = ArgumentCaptor.forClass(ProgramSearchCriteria.class);
		verify(programRepository).search(captor.capture());
		assertThat(captor.getValue().divisionId()).isEqualTo(divisionId);
	}

	@Test
	void listPrograms_filtersByStatus() {
		when(programRepository.search(any())).thenReturn(new ProgramSearchPage(List.of(), 0, 0));

		useCase.listPrograms(new ListAcademicProgramsQuery(ProgramStatus.ACTIVE, null, null, 0, 20));

		ArgumentCaptor<ProgramSearchCriteria> captor = ArgumentCaptor.forClass(ProgramSearchCriteria.class);
		verify(programRepository).search(captor.capture());
		assertThat(captor.getValue().status()).isEqualTo(ProgramStatus.ACTIVE);
	}

	@Test
	void listPrograms_mapsContentToSummaries() {
		List<AcademicProgram> content = List
				.of(new AcademicProgram(UUID.randomUUID(), "Ingenieria en Software", "Ingenieria en Software",
						"ISC-01", AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, "desc", null));
		when(programRepository.search(any())).thenReturn(new ProgramSearchPage(content, 1, 1));

		ListAcademicProgramsResult result = useCase
				.listPrograms(new ListAcademicProgramsQuery(null, null, null, 0, 20));

		assertThat(result.items()).hasSize(1).extracting(ProgramSummary::code).containsExactly("ISC-01");
	}
}
