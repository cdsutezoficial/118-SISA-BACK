package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.domain.port.in.SetPaymentRateUseCase.PaymentRateResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.SetPaymentRateUseCase.SetPaymentRateCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentRateRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentRateException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPaymentRateDataException;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentConceptReferenceNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PeriodNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.ProgramNotFoundException;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetPaymentRateUseCaseImplTest {

	@Mock
	private PaymentRateRepository paymentRateRepository;

	@Mock
	private PaymentConceptRepository paymentConceptRepository;

	@Mock
	private AcademicProgramRepository programRepository;

	@Mock
	private AcademicPeriodRepository periodRepository;

	private SetPaymentRateUseCaseImpl useCase;

	private UUID conceptId;

	private UUID programId;

	private UUID periodId;

	@BeforeEach
	void setUp() {
		useCase = new SetPaymentRateUseCaseImpl(paymentRateRepository, paymentConceptRepository, programRepository,
				periodRepository);
		conceptId = UUID.randomUUID();
		programId = UUID.randomUUID();
		periodId = UUID.randomUUID();
		// lenient: setRate_rejectsMissingConceptId short-circuits on a null
		// conceptId before ever calling findById, which would otherwise trip
		// Mockito's strict-stubs UnnecessaryStubbingException for that one test.
		lenient().when(paymentConceptRepository.findById(conceptId)).thenReturn(Optional.of(newConcept()));
	}

	@Test
	void setRate_rejectsNonExistentConceptId() {
		when(paymentConceptRepository.findById(conceptId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.setRate(continuousCommand(BigDecimal.valueOf(100), LocalDate.of(2026, 1, 1))))
				.isInstanceOf(PaymentConceptReferenceNotFoundException.class);

		verify(paymentRateRepository, never()).save(any());
	}

	@Test
	void setRate_rejectsMissingConceptId() {
		assertThatThrownBy(() -> useCase.setRate(new SetPaymentRateCommand(null, programId, AcademicLevel.LICENCIATURA,
				BigDecimal.valueOf(100), null, LocalDate.of(2026, 1, 1))))
				.isInstanceOf(PaymentConceptReferenceNotFoundException.class);
	}

	@Test
	void setRate_rejectsNonExistentProgramId() {
		when(programRepository.findById(programId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.setRate(continuousCommand(BigDecimal.valueOf(100), LocalDate.of(2026, 1, 1))))
				.isInstanceOf(ProgramNotFoundException.class);

		verify(paymentRateRepository, never()).save(any());
	}

	@Test
	void setRate_rejectsNonExistentPeriodId() {
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		when(periodRepository.findById(periodId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.setRate(periodScopedCommand(BigDecimal.valueOf(100))))
				.isInstanceOf(PeriodNotFoundException.class);

		verify(paymentRateRepository, never()).save(any());
	}

	@Test
	void setRate_rejectsZeroAmount() {
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));

		assertThatThrownBy(
				() -> useCase.setRate(continuousCommand(BigDecimal.ZERO, LocalDate.of(2026, 1, 1))))
				.isInstanceOf(InvalidPaymentRateDataException.class);

		verify(paymentRateRepository, never()).save(any());
	}

	@Test
	void setRate_rejectsNegativeAmount() {
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));

		assertThatThrownBy(() -> useCase
				.setRate(continuousCommand(BigDecimal.valueOf(-10), LocalDate.of(2026, 1, 1))))
				.isInstanceOf(InvalidPaymentRateDataException.class);
	}

	@Test
	void setRate_closingAContinuousRateSetsValidToToNewValidFromMinusOneDay() {
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		PaymentRate existingActive = new PaymentRate(conceptId, programId, AcademicLevel.LICENCIATURA,
				BigDecimal.valueOf(1000), null, LocalDate.of(2025, 1, 1));
		when(paymentRateRepository.findActiveContinuousRate(conceptId, programId, AcademicLevel.LICENCIATURA))
				.thenReturn(Optional.of(existingActive));
		when(paymentRateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		useCase.setRate(continuousCommand(BigDecimal.valueOf(1500), LocalDate.of(2026, 6, 1)));

		assertThat(existingActive.getValidTo()).isEqualTo(LocalDate.of(2026, 5, 31));
		verify(paymentRateRepository, times(2)).save(any());
	}

	@Test
	void setRate_creatingASecondContinuousRateClosesTheFirstWhichNoLongerShowsAsActive() {
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		PaymentRate first = new PaymentRate(conceptId, programId, AcademicLevel.LICENCIATURA, BigDecimal.valueOf(1000),
				null, LocalDate.of(2025, 1, 1));
		// First call: no active rate yet. After first insert, simulate that it
		// becomes the active one for a second call.
		when(paymentRateRepository.findActiveContinuousRate(conceptId, programId, AcademicLevel.LICENCIATURA))
				.thenReturn(Optional.empty(), Optional.of(first));
		when(paymentRateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		useCase.setRate(continuousCommand(BigDecimal.valueOf(1000), LocalDate.of(2025, 1, 1)));
		useCase.setRate(continuousCommand(BigDecimal.valueOf(1500), LocalDate.of(2026, 6, 1)));

		assertThat(first.getValidTo()).isEqualTo(LocalDate.of(2026, 5, 31));
	}

	@Test
	void setRate_periodScopedRateDoesNotCloseTheContinuousRateForTheSameCombination() {
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		when(periodRepository.findById(periodId)).thenReturn(Optional.of(newPeriod()));
		when(paymentRateRepository.existsByExactCombination(conceptId, programId, AcademicLevel.LICENCIATURA,
				periodId)).thenReturn(false);
		when(paymentRateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		useCase.setRate(periodScopedCommand(BigDecimal.valueOf(2000)));

		verify(paymentRateRepository, never()).findActiveContinuousRate(any(), any(), any());
		verify(paymentRateRepository, times(1)).save(any());
	}

	@Test
	void setRate_periodScopedRateDoesNotCloseADifferentPeriodsRateForTheSameCombination() {
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		when(periodRepository.findById(periodId)).thenReturn(Optional.of(newPeriod()));
		when(paymentRateRepository.existsByExactCombination(conceptId, programId, AcademicLevel.LICENCIATURA,
				periodId)).thenReturn(false);
		ArgumentCaptor<PaymentRate> captor = ArgumentCaptor.forClass(PaymentRate.class);
		when(paymentRateRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

		useCase.setRate(periodScopedCommand(BigDecimal.valueOf(2000)));

		assertThat(captor.getValue().getValidTo()).isNull();
		verify(paymentRateRepository, never()).findActiveContinuousRate(any(), any(), any());
	}

	@Test
	void setRate_rejectsDuplicatePeriodScopedRateForTheExactSameCombination() {
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		when(periodRepository.findById(periodId)).thenReturn(Optional.of(newPeriod()));
		when(paymentRateRepository.existsByExactCombination(conceptId, programId, AcademicLevel.LICENCIATURA,
				periodId)).thenReturn(true);

		assertThatThrownBy(() -> useCase.setRate(periodScopedCommand(BigDecimal.valueOf(2000))))
				.isInstanceOf(DuplicatePaymentRateException.class);

		verify(paymentRateRepository, never()).save(any());
	}

	@Test
	void setRate_rateForADifferentProgramOrLevelDoesNotGetClosed() {
		UUID otherProgramId = UUID.randomUUID();
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		// The active rate lookup is scoped to the EXACT combination — a rate
		// for a different program/level is a different casillero and is never
		// returned by this lookup, so it can never be closed as a side effect.
		when(paymentRateRepository.findActiveContinuousRate(conceptId, programId, AcademicLevel.LICENCIATURA))
				.thenReturn(Optional.empty());
		when(paymentRateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		useCase.setRate(continuousCommand(BigDecimal.valueOf(1500), LocalDate.of(2026, 6, 1)));

		verify(paymentRateRepository, never()).findActiveContinuousRate(otherProgramId, programId,
				AcademicLevel.LICENCIATURA);
	}

	@Test
	void setRate_successfulContinuousCreationReturnsExpectedResult() {
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		when(paymentRateRepository.findActiveContinuousRate(conceptId, programId, AcademicLevel.LICENCIATURA))
				.thenReturn(Optional.empty());
		when(paymentRateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentRateResult result = useCase
				.setRate(continuousCommand(BigDecimal.valueOf(1500), LocalDate.of(2026, 1, 1)));

		assertThat(result.conceptId()).isEqualTo(conceptId);
		assertThat(result.programId()).isEqualTo(programId);
		assertThat(result.level()).isEqualTo(AcademicLevel.LICENCIATURA);
		assertThat(result.amount()).isEqualByComparingTo(BigDecimal.valueOf(1500));
		assertThat(result.periodId()).isNull();
		assertThat(result.validTo()).isNull();
	}

	@Test
	void setRate_allowsNullProgramIdAndLevel() {
		when(paymentRateRepository.findActiveContinuousRate(conceptId, null, null)).thenReturn(Optional.empty());
		when(paymentRateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentRateResult result = useCase.setRate(
				new SetPaymentRateCommand(conceptId, null, null, BigDecimal.valueOf(500), null, LocalDate.of(2026, 1, 1)));

		assertThat(result.programId()).isNull();
		assertThat(result.level()).isNull();
	}

	private SetPaymentRateCommand continuousCommand(BigDecimal amount, LocalDate validFrom) {
		return new SetPaymentRateCommand(conceptId, programId, AcademicLevel.LICENCIATURA, amount, null, validFrom);
	}

	private SetPaymentRateCommand periodScopedCommand(BigDecimal amount) {
		return new SetPaymentRateCommand(conceptId, programId, AcademicLevel.LICENCIATURA, amount, periodId,
				LocalDate.of(2026, 1, 1));
	}

	private static PaymentConcept newConcept() {
		return new PaymentConcept("Inscripcion", null, null, PaymentConceptType.ENROLLMENT, true, false, null, null,
				false, null, null);
	}

	private static AcademicProgram newProgram() {
		return new AcademicProgram(UUID.randomUUID(), "Ingenieria en Software", "Ingenieria en Software", "PROG-1",
				AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, null, null);
	}

	private static AcademicPeriod newPeriod() {
		return new AcademicPeriod("Periodo 2026", 2026, 1, PeriodType.CUATRIMESTRAL, LocalDate.of(2026, 1, 5),
				LocalDate.of(2026, 4, 30), LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 20));
	}
}
