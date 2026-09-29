package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdvanceAcademicPeriodStatusByDateUseCaseImplTest {

	private static final LocalDate START = LocalDate.of(2026, 1, 5);
	private static final LocalDate END = LocalDate.of(2026, 4, 30);
	private static final LocalDate ENROLLMENT_START = LocalDate.of(2025, 12, 1);
	private static final LocalDate ENROLLMENT_END = LocalDate.of(2025, 12, 20);

	@Mock
	private AcademicPeriodRepository periodRepository;

	private AdvanceAcademicPeriodStatusByDateUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new AdvanceAcademicPeriodStatusByDateUseCaseImpl(periodRepository);
	}

	private static AcademicPeriod newPeriod() {
		return new AcademicPeriod("Enero-Abril 2026", 2026, 1, PeriodType.CUATRIMESTRAL, START, END,
				ENROLLMENT_START, ENROLLMENT_END);
	}

	private static AcademicPeriod newPeriodWithLaterEnrollment() {
		return new AcademicPeriod("Mayo-Agosto 2026", 2026, 2, PeriodType.CUATRIMESTRAL, START.plusMonths(4),
				END.plusMonths(4), ENROLLMENT_START.plusMonths(4), ENROLLMENT_END.plusMonths(4));
	}

	@Test
	void advanceAll_advancesPeriodsWhoseDateThresholdHasPassed_andOnlySavesThose() {
		AcademicPeriod maturing = newPeriod();
		AcademicPeriod notYet = newPeriodWithLaterEnrollment();
		when(periodRepository.findAll()).thenReturn(List.of(maturing, notYet));
		when(periodRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		int changed = useCase.advanceAll(ENROLLMENT_START.plusDays(1));

		assertThat(changed).isEqualTo(1);
		assertThat(maturing.getStatus()).isEqualTo(PeriodStatus.ENROLLMENT);
		assertThat(notYet.getStatus()).isEqualTo(PeriodStatus.CONFIGURATION);
		verify(periodRepository).save(maturing);
		verify(periodRepository, never()).save(notYet);
	}

	@Test
	void advanceAll_returnsZero_whenNoPeriodIsMature() {
		when(periodRepository.findAll()).thenReturn(List.of(newPeriod(), newPeriod()));

		int changed = useCase.advanceAll(ENROLLMENT_START.minusDays(1));

		assertThat(changed).isZero();
		verify(periodRepository, never()).save(any());
	}

	@Test
	void advanceAll_leavesClosedPeriodsUntouched() {
		AcademicPeriod closed = newPeriod();
		closed.changeStatus(PeriodStatus.ENROLLMENT);
		closed.changeStatus(PeriodStatus.ACTIVE);
		closed.changeStatus(PeriodStatus.CLOSED);
		when(periodRepository.findAll()).thenReturn(List.of(closed));

		int changed = useCase.advanceAll(END.plusDays(30));

		assertThat(changed).isZero();
		assertThat(closed.getStatus()).isEqualTo(PeriodStatus.CLOSED);
		verify(periodRepository, never()).save(any());
	}

	@Test
	void advanceAll_handlesZeroPeriods() {
		when(periodRepository.findAll()).thenReturn(List.of());

		int changed = useCase.advanceAll(END.plusDays(1));

		assertThat(changed).isZero();
	}
}