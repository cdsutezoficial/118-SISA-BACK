package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase.PeriodResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicPeriodUseCase.UpdatePeriodCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPeriodNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePeriodException;
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
class UpdateAcademicPeriodUseCaseImplTest {

	private static final LocalDate START = LocalDate.of(2026, 1, 5);
	private static final LocalDate END = LocalDate.of(2026, 4, 30);
	private static final LocalDate ENROLLMENT_START = LocalDate.of(2025, 12, 1);
	private static final LocalDate ENROLLMENT_END = LocalDate.of(2025, 12, 20);

	@Mock
	private AcademicPeriodRepository periodRepository;

	private UpdateAcademicPeriodUseCaseImpl useCase;

	private AcademicPeriod periodA;
	private UUID periodAId;

	@BeforeEach
	void setUp() {
		useCase = new UpdateAcademicPeriodUseCaseImpl(periodRepository);
		periodA = newPeriod("Enero-Abril 2026", 2026, 1);
		periodAId = UUID.randomUUID();
		ReflectionTestUtils.setField(periodA, "id", periodAId);
	}

	@Test
	void updatePeriod_successfulUpdateLeavesStatusUnchanged() {
		when(periodRepository.findById(periodAId)).thenReturn(Optional.of(periodA));
		when(periodRepository.findByYearAndPeriodNumber(2026, 2)).thenReturn(Optional.empty());
		when(periodRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PeriodResult result = useCase.updatePeriod(new UpdatePeriodCommand(periodAId, "Renombrado", 2026, 2,
				PeriodType.SEMESTRAL, START, END, ENROLLMENT_START, ENROLLMENT_END));

		assertThat(result.name()).isEqualTo("Renombrado");
		assertThat(result.periodNumber()).isEqualTo(2);
		assertThat(result.status()).isEqualTo(PeriodStatus.CONFIGURATION);
	}

	@Test
	void updatePeriod_allowsKeepingItsOwnCurrentYearAndPeriodNumber() {
		when(periodRepository.findById(periodAId)).thenReturn(Optional.of(periodA));
		when(periodRepository.findByYearAndPeriodNumber(2026, 1)).thenReturn(Optional.of(periodA));
		when(periodRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PeriodResult result = useCase.updatePeriod(new UpdatePeriodCommand(periodAId, "Renombrado", 2026, 1,
				PeriodType.CUATRIMESTRAL, START, END, ENROLLMENT_START, ENROLLMENT_END));

		assertThat(result.name()).isEqualTo("Renombrado");
		assertThat(result.year()).isEqualTo(2026);
		assertThat(result.periodNumber()).isEqualTo(1);
	}

	@Test
	void updatePeriod_rejectsYearAndPeriodNumberConflictWithAnotherPeriod() {
		AcademicPeriod periodB = newPeriod("Mayo-Agosto 2026", 2026, 2);
		UUID periodBId = UUID.randomUUID();
		ReflectionTestUtils.setField(periodB, "id", periodBId);
		when(periodRepository.findById(periodBId)).thenReturn(Optional.of(periodB));
		when(periodRepository.findByYearAndPeriodNumber(2026, 1)).thenReturn(Optional.of(periodA));

		assertThatThrownBy(() -> useCase.updatePeriod(new UpdatePeriodCommand(periodBId, "Mayo-Agosto 2026", 2026, 1,
				PeriodType.CUATRIMESTRAL, START, END, ENROLLMENT_START, ENROLLMENT_END)))
				.isInstanceOf(DuplicatePeriodException.class);

		assertThat(periodB.getPeriodNumber()).isEqualTo(2);
	}

	@Test
	void updatePeriod_rejectsUnknownPeriodId() {
		UUID unknownId = UUID.randomUUID();
		when(periodRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.updatePeriod(new UpdatePeriodCommand(unknownId, "X", 2026, 1,
				PeriodType.CUATRIMESTRAL, START, END, ENROLLMENT_START, ENROLLMENT_END)))
				.isInstanceOf(AcademicPeriodNotFoundException.class);
	}

	private static AcademicPeriod newPeriod(String name, int year, int periodNumber) {
		return new AcademicPeriod(name, year, periodNumber, PeriodType.CUATRIMESTRAL, START, END, ENROLLMENT_START,
				ENROLLMENT_END);
	}
}
