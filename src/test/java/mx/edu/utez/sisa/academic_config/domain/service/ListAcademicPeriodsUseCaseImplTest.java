package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPeriodsUseCase.ListAcademicPeriodsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPeriodsUseCase.ListAcademicPeriodsResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPeriodsUseCase.PeriodSummary;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository.PeriodSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository.PeriodSearchPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListAcademicPeriodsUseCaseImplTest {

	private static final LocalDate START = LocalDate.of(2026, 1, 5);
	private static final LocalDate END = LocalDate.of(2026, 4, 30);
	private static final LocalDate ENROLLMENT_START = LocalDate.of(2025, 12, 1);
	private static final LocalDate ENROLLMENT_END = LocalDate.of(2025, 12, 20);

	@Mock
	private AcademicPeriodRepository periodRepository;

	private ListAcademicPeriodsUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ListAcademicPeriodsUseCaseImpl(periodRepository);
	}

	@Test
	void listPeriods_defaultPaginationUsesDefaultSize() {
		List<AcademicPeriod> content = List.of(newPeriod("Enero-Abril 2026", 2026, 1),
				newPeriod("Mayo-Agosto 2026", 2026, 2));
		when(periodRepository.search(any())).thenReturn(new PeriodSearchPage(content, 22, 2));

		ListAcademicPeriodsResult result = useCase.listPeriods(new ListAcademicPeriodsQuery(null, null, 0, 0));

		ArgumentCaptor<PeriodSearchCriteria> captor = ArgumentCaptor.forClass(PeriodSearchCriteria.class);
		verify(periodRepository).search(captor.capture());
		assertThat(captor.getValue().size()).isEqualTo(ListAcademicPeriodsQuery.DEFAULT_PAGE_SIZE);
		assertThat(captor.getValue().page()).isZero();
		assertThat(result.totalElements()).isEqualTo(22);
		assertThat(result.totalPages()).isEqualTo(2);
	}

	@Test
	void listPeriods_mapsEntitiesToSummaries() {
		List<AcademicPeriod> content = List.of(newPeriod("Enero-Abril 2026", 2026, 1),
				newPeriod("Mayo-Agosto 2026", 2026, 2));
		when(periodRepository.search(any())).thenReturn(new PeriodSearchPage(content, 2, 1));

		ListAcademicPeriodsResult result = useCase.listPeriods(new ListAcademicPeriodsQuery(null, null, 0, 20));

		assertThat(result.items()).hasSize(2).extracting(PeriodSummary::name)
				.containsExactlyInAnyOrder("Enero-Abril 2026", "Mayo-Agosto 2026");
	}

	@Test
	void listPeriods_oversizedPageIsCappedAtMax() {
		when(periodRepository.search(any())).thenReturn(new PeriodSearchPage(List.of(), 0, 0));

		useCase.listPeriods(new ListAcademicPeriodsQuery(null, null, 0, 500));

		ArgumentCaptor<PeriodSearchCriteria> captor = ArgumentCaptor.forClass(PeriodSearchCriteria.class);
		verify(periodRepository).search(captor.capture());
		assertThat(captor.getValue().size()).isEqualTo(ListAcademicPeriodsQuery.MAX_PAGE_SIZE);
	}

	@Test
	void listPeriods_negativePageIsNormalizedToZero() {
		when(periodRepository.search(any())).thenReturn(new PeriodSearchPage(List.of(), 0, 0));

		useCase.listPeriods(new ListAcademicPeriodsQuery(null, null, -5, 20));

		ArgumentCaptor<PeriodSearchCriteria> captor = ArgumentCaptor.forClass(PeriodSearchCriteria.class);
		verify(periodRepository).search(captor.capture());
		assertThat(captor.getValue().page()).isZero();
	}

	private static AcademicPeriod newPeriod(String name, int year, int periodNumber) {
		return new AcademicPeriod(name, year, periodNumber, PeriodType.CUATRIMESTRAL, START, END, ENROLLMENT_START,
				ENROLLMENT_END);
	}
}
