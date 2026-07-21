package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicPeriodStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase.PeriodResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPeriodNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPeriodStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeAcademicPeriodStatusUseCaseImplTest {

	private static final LocalDate START = LocalDate.of(2026, 1, 5);
	private static final LocalDate END = LocalDate.of(2026, 4, 30);
	private static final LocalDate ENROLLMENT_START = LocalDate.of(2025, 12, 1);
	private static final LocalDate ENROLLMENT_END = LocalDate.of(2025, 12, 20);

	@Mock
	private AcademicPeriodRepository periodRepository;

	private ChangeAcademicPeriodStatusUseCaseImpl useCase;

	private AcademicPeriod period;
	private UUID periodId;

	@BeforeEach
	void setUp() {
		useCase = new ChangeAcademicPeriodStatusUseCaseImpl(periodRepository);
		period = new AcademicPeriod("Enero-Abril 2026", 2026, 1, PeriodType.CUATRIMESTRAL, START, END,
				ENROLLMENT_START, ENROLLMENT_END);
		periodId = UUID.randomUUID();
		ReflectionTestUtils.setField(period, "id", periodId);
	}

	@Test
	void changeStatus_advancesFromConfigurationToEnrollment() {
		when(periodRepository.findById(periodId)).thenReturn(Optional.of(period));
		when(periodRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PeriodResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), periodId, PeriodStatus.ENROLLMENT));

		assertThat(result.status()).isEqualTo(PeriodStatus.ENROLLMENT);
	}

	@Test
	void changeStatus_rejectsSkippingConfigurationToActive() {
		when(periodRepository.findById(periodId)).thenReturn(Optional.of(period));

		assertThatThrownBy(() -> useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), periodId, PeriodStatus.ACTIVE)))
				.isInstanceOf(InvalidPeriodStatusTransitionException.class);
	}

	@Test
	void changeStatus_rejectsUnknownPeriodId() {
		UUID unknownId = UUID.randomUUID();
		when(periodRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), unknownId, PeriodStatus.ENROLLMENT)))
				.isInstanceOf(AcademicPeriodNotFoundException.class);
	}
}
