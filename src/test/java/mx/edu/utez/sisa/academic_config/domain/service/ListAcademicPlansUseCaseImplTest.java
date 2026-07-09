package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.PlanStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPlansUseCase.ListAcademicPlansQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPlansUseCase.ListAcademicPlansResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPlansUseCase.PlanSummary;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository.PlanSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository.PlanSearchPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListAcademicPlansUseCaseImplTest {

	@Mock
	private AcademicPlanRepository planRepository;

	private ListAcademicPlansUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ListAcademicPlansUseCaseImpl(planRepository);
	}

	@Test
	void listPlans_defaultReturnsAllStatusesWithDefaultPageSize() {
		UUID programId = UUID.randomUUID();
		List<AcademicPlan> content = List.of(
				new AcademicPlan(programId, "2022-A", "Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1), 6,
						BigDecimal.valueOf(6.0), 3, false, null),
				new AcademicPlan(programId, "2023-A", "Enero 2023", "TIT-002", LocalDate.of(2023, 1, 1), 7,
						BigDecimal.valueOf(7.0), 2, false, null));
		when(planRepository.search(any())).thenReturn(new PlanSearchPage(content, 22, 2));

		ListAcademicPlansResult result = useCase.listPlans(new ListAcademicPlansQuery(null, null, null, 0, 0));

		ArgumentCaptor<PlanSearchCriteria> captor = ArgumentCaptor.forClass(PlanSearchCriteria.class);
		verify(planRepository).search(captor.capture());
		assertThat(captor.getValue().status()).isNull();
		assertThat(captor.getValue().size()).isEqualTo(ListAcademicPlansQuery.DEFAULT_PAGE_SIZE);
		assertThat(captor.getValue().page()).isZero();
		assertThat(result.totalElements()).isEqualTo(22);
		assertThat(result.totalPages()).isEqualTo(2);
	}

	@Test
	void listPlans_filtersByProgramId() {
		UUID programId = UUID.randomUUID();
		when(planRepository.search(any())).thenReturn(new PlanSearchPage(List.of(), 0, 0));

		useCase.listPlans(new ListAcademicPlansQuery(null, null, programId, 0, 20));

		ArgumentCaptor<PlanSearchCriteria> captor = ArgumentCaptor.forClass(PlanSearchCriteria.class);
		verify(planRepository).search(captor.capture());
		assertThat(captor.getValue().programId()).isEqualTo(programId);
	}

	@Test
	void listPlans_filtersByStatus() {
		when(planRepository.search(any())).thenReturn(new PlanSearchPage(List.of(), 0, 0));

		useCase.listPlans(new ListAcademicPlansQuery(PlanStatus.ACTIVE, null, null, 0, 20));

		ArgumentCaptor<PlanSearchCriteria> captor = ArgumentCaptor.forClass(PlanSearchCriteria.class);
		verify(planRepository).search(captor.capture());
		assertThat(captor.getValue().status()).isEqualTo(PlanStatus.ACTIVE);
	}

	@Test
	void listPlans_mapsContentToSummaries() {
		UUID programId = UUID.randomUUID();
		List<AcademicPlan> content = List.of(new AcademicPlan(programId, "2022-A", "Septiembre 2022", "TIT-001",
				LocalDate.of(2022, 9, 1), 6, BigDecimal.valueOf(6.0), 3, false, null));
		when(planRepository.search(any())).thenReturn(new PlanSearchPage(content, 1, 1));

		ListAcademicPlansResult result = useCase.listPlans(new ListAcademicPlansQuery(null, null, null, 0, 20));

		assertThat(result.items()).hasSize(1).extracting(PlanSummary::version).containsExactly("2022-A");
	}
}
