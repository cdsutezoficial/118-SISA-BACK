package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicDivisionsUseCase.DivisionSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicDivisionsUseCase.ListAcademicDivisionsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicDivisionsUseCase.ListAcademicDivisionsResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository.DivisionSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository.DivisionSearchPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListAcademicDivisionsUseCaseImplTest {

	@Mock
	private AcademicDivisionRepository divisionRepository;

	private ListAcademicDivisionsUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ListAcademicDivisionsUseCaseImpl(divisionRepository);
	}

	@Test
	void listDivisions_defaultPaginationUsesDefaultSize() {
		List<AcademicDivision> content = List.of(new AcademicDivision("Diseno", "DSC", "desc", null),
				new AcademicDivision("Industrial", "DIN", "desc", null));
		when(divisionRepository.search(any())).thenReturn(new DivisionSearchPage(content, 22, 2));

		ListAcademicDivisionsResult result = useCase.listDivisions(new ListAcademicDivisionsQuery(null, null, 0, 0));

		ArgumentCaptor<DivisionSearchCriteria> captor = ArgumentCaptor.forClass(DivisionSearchCriteria.class);
		verify(divisionRepository).search(captor.capture());
		assertThat(captor.getValue().size()).isEqualTo(ListAcademicDivisionsQuery.DEFAULT_PAGE_SIZE);
		assertThat(captor.getValue().page()).isZero();
		assertThat(result.totalElements()).isEqualTo(22);
		assertThat(result.totalPages()).isEqualTo(2);
	}

	@Test
	void listDivisions_everyItemReportsStubProgramCount() {
		List<AcademicDivision> content = List.of(new AcademicDivision("Diseno", "DSC", "desc", null),
				new AcademicDivision("Industrial", "DIN", "desc", null));
		when(divisionRepository.search(any())).thenReturn(new DivisionSearchPage(content, 2, 1));

		ListAcademicDivisionsResult result = useCase.listDivisions(new ListAcademicDivisionsQuery(null, null, 0, 20));

		assertThat(result.items()).hasSize(2).extracting(DivisionSummary::programCount).containsOnly(0);
	}
}
