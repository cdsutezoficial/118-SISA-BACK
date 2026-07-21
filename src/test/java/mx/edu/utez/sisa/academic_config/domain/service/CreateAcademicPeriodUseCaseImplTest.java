package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase.CreatePeriodCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase.PeriodResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePeriodException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPlanDataException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateAcademicPeriodUseCaseImplTest {

	private static final LocalDate START = LocalDate.of(2026, 1, 5);
	private static final LocalDate END = LocalDate.of(2026, 4, 30);
	private static final LocalDate ENROLLMENT_START = LocalDate.of(2025, 12, 1);
	private static final LocalDate ENROLLMENT_END = LocalDate.of(2025, 12, 20);

	@Mock
	private AcademicPeriodRepository periodRepository;

	private CreateAcademicPeriodUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new CreateAcademicPeriodUseCaseImpl(periodRepository);
	}

	@Test
	void createPeriod_successfulCreation() {
		when(periodRepository.findByYearAndPeriodNumber(2026, 1)).thenReturn(Optional.empty());
		when(periodRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PeriodResult result = useCase.createPeriod(command());

		assertThat(result.status()).isEqualTo(PeriodStatus.CONFIGURATION);
		assertThat(result.name()).isEqualTo("Enero-Abril 2026");
		assertThat(result.year()).isEqualTo(2026);
		assertThat(result.periodNumber()).isEqualTo(1);
	}

	@Test
	void createPeriod_rejectsDuplicateYearAndPeriodNumber() {
		AcademicPeriod existing = newPeriod();
		when(periodRepository.findByYearAndPeriodNumber(2026, 1)).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> useCase.createPeriod(command())).isInstanceOf(DuplicatePeriodException.class);

		verify(periodRepository, never()).save(any());
	}

	@Test
	void createPeriod_allowsSamePeriodNumberInDifferentYears() {
		when(periodRepository.findByYearAndPeriodNumber(2027, 1)).thenReturn(Optional.empty());
		when(periodRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PeriodResult result = useCase.createPeriod(new CreatePeriodCommand("Enero-Abril 2027", 2027, 1,
				PeriodType.CUATRIMESTRAL, START.plusYears(1), END.plusYears(1), ENROLLMENT_START.plusYears(1),
				ENROLLMENT_END.plusYears(1)));

		assertThat(result.year()).isEqualTo(2027);
	}

	@Test
	void createPeriod_propagatesInvalidDateRangeFromTheEntity() {
		when(periodRepository.findByYearAndPeriodNumber(2026, 1)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.createPeriod(new CreatePeriodCommand("Invalid", 2026, 1,
				PeriodType.CUATRIMESTRAL, END, START, ENROLLMENT_START, ENROLLMENT_END)))
				.isInstanceOf(InvalidPlanDataException.class);

		verify(periodRepository, never()).save(any());
	}

	private static CreatePeriodCommand command() {
		return new CreatePeriodCommand("Enero-Abril 2026", 2026, 1, PeriodType.CUATRIMESTRAL, START, END,
				ENROLLMENT_START, ENROLLMENT_END);
	}

	private static AcademicPeriod newPeriod() {
		return new AcademicPeriod("Enero-Abril 2026", 2026, 1, PeriodType.CUATRIMESTRAL, START, END, ENROLLMENT_START,
				ENROLLMENT_END);
	}
}
