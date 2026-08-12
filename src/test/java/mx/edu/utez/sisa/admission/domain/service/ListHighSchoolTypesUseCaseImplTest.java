package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
import mx.edu.utez.sisa.admission.domain.port.in.ListHighSchoolTypesUseCase.ListHighSchoolTypesQuery;
import mx.edu.utez.sisa.admission.domain.port.in.ListHighSchoolTypesUseCase.ListHighSchoolTypesResult;
import mx.edu.utez.sisa.admission.domain.port.in.ListHighSchoolTypesUseCase.HighSchoolTypeSummary;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository.HighSchoolTypeSearchCriteria;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository.HighSchoolTypeSearchPage;
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
class ListHighSchoolTypesUseCaseImplTest {

	@Mock
	private HighSchoolTypeRepository highSchoolTypeRepository;

	private ListHighSchoolTypesUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ListHighSchoolTypesUseCaseImpl(highSchoolTypeRepository);
	}

	@Test
	void listHighSchoolTypes_defaultPaginationUsesDefaultSize() {
		List<HighSchoolType> content = List.of(new HighSchoolType("Conalep"), new HighSchoolType("Cobaem"));
		when(highSchoolTypeRepository.search(any())).thenReturn(new HighSchoolTypeSearchPage(content, 22, 2));

		ListHighSchoolTypesResult result = useCase.listHighSchoolTypes(new ListHighSchoolTypesQuery(null, null, 0, 0));

		ArgumentCaptor<HighSchoolTypeSearchCriteria> captor = ArgumentCaptor
				.forClass(HighSchoolTypeSearchCriteria.class);
		verify(highSchoolTypeRepository).search(captor.capture());
		assertThat(captor.getValue().size()).isEqualTo(ListHighSchoolTypesQuery.DEFAULT_PAGE_SIZE);
		assertThat(captor.getValue().page()).isZero();
		assertThat(result.totalElements()).isEqualTo(22);
		assertThat(result.totalPages()).isEqualTo(2);
	}

	@Test
	void listHighSchoolTypes_mapsEntitiesToSummaries() {
		List<HighSchoolType> content = List.of(new HighSchoolType("Conalep"), new HighSchoolType("Cobaem"));
		when(highSchoolTypeRepository.search(any())).thenReturn(new HighSchoolTypeSearchPage(content, 2, 1));

		ListHighSchoolTypesResult result = useCase
				.listHighSchoolTypes(new ListHighSchoolTypesQuery(null, null, 0, 20));

		assertThat(result.items()).hasSize(2).extracting(HighSchoolTypeSummary::name)
				.containsExactlyInAnyOrder("Conalep", "Cobaem");
	}

	@Test
	void listHighSchoolTypes_oversizedPageIsCappedAtMax() {
		when(highSchoolTypeRepository.search(any())).thenReturn(new HighSchoolTypeSearchPage(List.of(), 0, 0));

		useCase.listHighSchoolTypes(new ListHighSchoolTypesQuery(null, null, 0, 500));

		ArgumentCaptor<HighSchoolTypeSearchCriteria> captor = ArgumentCaptor
				.forClass(HighSchoolTypeSearchCriteria.class);
		verify(highSchoolTypeRepository).search(captor.capture());
		assertThat(captor.getValue().size()).isEqualTo(ListHighSchoolTypesQuery.MAX_PAGE_SIZE);
	}

	@Test
	void listHighSchoolTypes_negativePageIsNormalizedToZero() {
		when(highSchoolTypeRepository.search(any())).thenReturn(new HighSchoolTypeSearchPage(List.of(), 0, 0));

		useCase.listHighSchoolTypes(new ListHighSchoolTypesQuery(null, null, -5, 20));

		ArgumentCaptor<HighSchoolTypeSearchCriteria> captor = ArgumentCaptor
				.forClass(HighSchoolTypeSearchCriteria.class);
		verify(highSchoolTypeRepository).search(captor.capture());
		assertThat(captor.getValue().page()).isZero();
	}
}
