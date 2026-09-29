package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetConfigurationStatisticsUseCase.GetConfigurationStatisticsResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.ConfigurationStatisticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetConfigurationStatisticsUseCaseImplTest {

	private static final LocalDate START = LocalDate.of(2026, 1, 5);
	private static final LocalDate END = LocalDate.of(2026, 4, 30);
	private static final LocalDate ENROLLMENT_START = LocalDate.of(2025, 12, 1);
	private static final LocalDate ENROLLMENT_END = LocalDate.of(2025, 12, 20);

	@Mock
	private ConfigurationStatisticsRepository statisticsRepository;

	@Mock
	private AcademicPeriodRepository periodRepository;

	private GetConfigurationStatisticsUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new GetConfigurationStatisticsUseCaseImpl(statisticsRepository, periodRepository);
	}

	@Test
	void getStatistics_activePeriodScopesGroupCounter() {
		AcademicPeriod older = period("Mayo-Agosto 2025", 2025, 2, PeriodStatus.ACTIVE);
		AcademicPeriod current = period("Mayo-Agosto 2026", 2026, 2, PeriodStatus.ACTIVE);
		when(periodRepository.findAll()).thenReturn(List.of(older, current));
		when(statisticsRepository.countDivisions()).thenReturn(4L);
		when(statisticsRepository.countPrograms()).thenReturn(12L);
		when(statisticsRepository.countSubjects()).thenReturn(148L);
		when(statisticsRepository.countGroupsForPeriod(current.getId())).thenReturn(36L);

		GetConfigurationStatisticsResult result = useCase.getStatistics();

		assertThat(result.divisions()).isEqualTo(4);
		assertThat(result.programs()).isEqualTo(12);
		assertThat(result.subjects()).isEqualTo(148);
		assertThat(result.groupsForCurrentPeriod()).isEqualTo(36);
		assertThat(result.currentPeriod()).isNotNull();
		assertThat(result.currentPeriod().id()).isEqualTo(current.getId());
		assertThat(result.currentPeriod().name()).isEqualTo("Mayo-Agosto 2026");
		verify(statisticsRepository).countGroupsForPeriod(current.getId());
	}

	@Test
	void getStatistics_fallsBackToMostRecentPeriodWhenNoneActive() {
		AcademicPeriod latest = period("Septiembre-Diciembre 2026", 2026, 3, PeriodStatus.CONFIGURATION);
		AcademicPeriod older = period("Mayo-Agosto 2026", 2026, 2, PeriodStatus.ENROLLMENT);
		when(periodRepository.findAll()).thenReturn(List.of(latest, older));
		when(statisticsRepository.countDivisions()).thenReturn(4L);
		when(statisticsRepository.countPrograms()).thenReturn(12L);
		when(statisticsRepository.countSubjects()).thenReturn(148L);
		when(statisticsRepository.countGroupsForPeriod(latest.getId())).thenReturn(9L);

		GetConfigurationStatisticsResult result = useCase.getStatistics();

		assertThat(result.currentPeriod().name()).isEqualTo("Septiembre-Diciembre 2026");
		assertThat(result.groupsForCurrentPeriod()).isEqualTo(9);
		verify(statisticsRepository).countGroupsForPeriod(latest.getId());
	}

	@Test
	void getStatistics_noPeriodsReportsNullCurrentAndZeroGroups() {
		when(periodRepository.findAll()).thenReturn(List.of());
		when(statisticsRepository.countDivisions()).thenReturn(4L);
		when(statisticsRepository.countPrograms()).thenReturn(12L);
		when(statisticsRepository.countSubjects()).thenReturn(148L);

		GetConfigurationStatisticsResult result = useCase.getStatistics();

		assertThat(result.groupsForCurrentPeriod()).isZero();
		assertThat(result.currentPeriod()).isNull();
		verify(statisticsRepository, never()).countGroupsForPeriod(any());
	}

	private static AcademicPeriod period(String name, int year, int periodNumber, PeriodStatus status) {
		AcademicPeriod period = new AcademicPeriod(name, year, periodNumber, PeriodType.CUATRIMESTRAL, START, END,
				ENROLLMENT_START, ENROLLMENT_END);
		ReflectionTestUtils.setField(period, "id", UUID.randomUUID());
		if (status == PeriodStatus.ENROLLMENT || status == PeriodStatus.ACTIVE || status == PeriodStatus.CLOSED) {
			period.changeStatus(PeriodStatus.ENROLLMENT);
		}
		if (status == PeriodStatus.ACTIVE || status == PeriodStatus.CLOSED) {
			period.changeStatus(PeriodStatus.ACTIVE);
		}
		return period;
	}
}