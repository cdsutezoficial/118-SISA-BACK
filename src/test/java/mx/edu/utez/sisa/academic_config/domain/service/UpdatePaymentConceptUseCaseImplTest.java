package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentConceptResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdatePaymentConceptUseCase.UpdatePaymentConceptCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentRateRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.IncompletePaymentRateSetException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPaymentConceptDataException;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentConceptNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdatePaymentConceptUseCaseImplTest {

	@Mock
	private PaymentConceptRepository paymentConceptRepository;

	@Mock
	private PaymentAreaRepository paymentAreaRepository;

	@Mock
	private PaymentRateRepository paymentRateRepository;

	@Mock
	private PaymentQuotaCoverageChecker coverageChecker;

	private UpdatePaymentConceptUseCaseImpl useCase;

	private PaymentConcept concept;
	private UUID conceptId;

	@BeforeEach
	void setUp() {
		useCase = new UpdatePaymentConceptUseCaseImpl(paymentConceptRepository, paymentAreaRepository,
				paymentRateRepository, coverageChecker);
		concept = new PaymentConcept("Inscripcion", "COD-1", "Descripcion", "Politicas", PaymentConceptType.ENROLLMENT, null,
				false, 1, 2, true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
		conceptId = UUID.randomUUID();
		ReflectionTestUtils.setField(concept, "id", conceptId);
	}

	@Test
	void updatePaymentConcept_successfulUpdateLeavesStatusUnchanged() {
		when(paymentConceptRepository.findById(conceptId)).thenReturn(Optional.of(concept));
		when(paymentConceptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentConceptResult result = useCase.updatePaymentConcept(commandWith(conceptId, "Reinscripcion", 3, 4,
				LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30)));

		assertThat(result.name()).isEqualTo("Reinscripcion");
		assertThat(result.maxPerStudent()).isEqualTo(3);
		assertThat(result.maxPerPeriod()).isEqualTo(4);
		assertThat(result.status()).isEqualTo(PaymentConceptStatus.ACTIVE);
	}

	@Test
	void updatePaymentConcept_rejectsUnknownPaymentConceptId() {
		UUID unknownId = UUID.randomUUID();
		when(paymentConceptRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.updatePaymentConcept(
				commandWith(unknownId, "Inscripcion", 1, 2, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))))
				.isInstanceOf(PaymentConceptNotFoundException.class);
	}

	@Test
	void updatePaymentConcept_rejectsZeroMaxPerStudent() {
		when(paymentConceptRepository.findById(conceptId)).thenReturn(Optional.of(concept));

		assertThatThrownBy(() -> useCase.updatePaymentConcept(
				commandWith(conceptId, "Inscripcion", 0, 2, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))))
				.isInstanceOf(InvalidPaymentConceptDataException.class);

		verify(paymentConceptRepository, never()).save(any());
	}

	@Test
	void updatePaymentConcept_rejectsNegativeMaxPerPeriod() {
		when(paymentConceptRepository.findById(conceptId)).thenReturn(Optional.of(concept));

		assertThatThrownBy(() -> useCase.updatePaymentConcept(
				commandWith(conceptId, "Inscripcion", 1, -3, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))))
				.isInstanceOf(InvalidPaymentConceptDataException.class);
	}

	@Test
	void updatePaymentConcept_rejectsAvailableFromAfterAvailableUntil() {
		when(paymentConceptRepository.findById(conceptId)).thenReturn(Optional.of(concept));

		assertThatThrownBy(() -> useCase.updatePaymentConcept(commandWith(conceptId, "Inscripcion", 1, 2,
				LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1)))).isInstanceOf(
				InvalidPaymentConceptDataException.class);

		verify(paymentConceptRepository, never()).save(any());
	}

	@Test
	void updatePaymentConcept_rejectsSelfLinkedConcept() {
		when(paymentConceptRepository.findById(conceptId)).thenReturn(Optional.of(concept));
		UpdatePaymentConceptCommand command = new UpdatePaymentConceptCommand(conceptId, "Inscripcion", "COD-1",
				"Descripcion", "Politicas", PaymentConceptType.ENROLLMENT, null, false, 1, 2, true, null, null, null,
				null, false, null, false, false, null, List.of(conceptId));

		assertThatThrownBy(() -> useCase.updatePaymentConcept(command))
				.isInstanceOf(InvalidPaymentConceptDataException.class);

		verify(paymentConceptRepository, never()).save(any());
	}

	private static UpdatePaymentConceptCommand commandWith(UUID id, String name, Integer maxPerStudent,
			Integer maxPerPeriod, LocalDate availableFrom, LocalDate availableUntil) {
		return new UpdatePaymentConceptCommand(id, name, "COD-1", "Descripcion", "Politicas", PaymentConceptType.ENROLLMENT,
				null, false, maxPerStudent, maxPerPeriod, true, availableFrom, availableUntil, null, null, false, null,
				false, false, null, List.of());
	}

	private static UpdatePaymentConceptCommand quotaCommand(UUID id, Integer levelNumber) {
		return new UpdatePaymentConceptCommand(id, "Cuota", "COD-CUOTA", "Descripcion", "Politicas",
				PaymentConceptType.PERIODIC_QUOTA, levelNumber, false, 1, 2, true, null, null, null, null, false, null,
				false, false, null, List.of());
	}

	/**
	 * The gap this guard closes: every field-level check is happy with the retype,
	 * so without the coverage read a live ENROLLMENT concept becomes a quota that
	 * charges nothing to anybody.
	 */
	@Test
	void updatePaymentConcept_rejectsRetypeToQuotaWhenTheRateSetIsIncomplete() {
		when(paymentConceptRepository.findById(conceptId)).thenReturn(Optional.of(concept));
		when(paymentRateRepository.findActiveByConceptId(conceptId)).thenReturn(List.of());
		org.mockito.Mockito.doThrow(new IncompletePaymentRateSetException(
				"A PERIODIC_QUOTA concept must price every active program. Missing: [Software]"))
				.when(coverageChecker).requireCompleteCoverage(Set.of());

		assertThatThrownBy(() -> useCase.updatePaymentConcept(quotaCommand(conceptId, 1)))
				.isInstanceOf(IncompletePaymentRateSetException.class);

		verify(paymentConceptRepository, never()).save(any());
	}

	@Test
	void updatePaymentConcept_acceptsRetypeToQuotaWhenTheRateSetIsComplete() {
		when(paymentConceptRepository.findById(conceptId)).thenReturn(Optional.of(concept));
		UUID programId = UUID.randomUUID();
		when(paymentRateRepository.findActiveByConceptId(conceptId))
				.thenReturn(List.of(new PaymentRate(conceptId, programId, null, BigDecimal.valueOf(500), null,
						LocalDateTime.now())));
		when(paymentConceptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentConceptResult result = useCase.updatePaymentConcept(quotaCommand(conceptId, 1));

		assertThat(result.type()).isEqualTo(PaymentConceptType.PERIODIC_QUOTA);
		assertThat(result.levelNumber()).isEqualTo(1);
	}

	/**
	 * A superseded rate is history, not coverage. Counting it here would let a
	 * concept claim to price a career it no longer charges.
	 */
	@Test
	void updatePaymentConcept_ignoresInactiveRatesWhenCheckingCoverage() {
		when(paymentConceptRepository.findById(conceptId)).thenReturn(Optional.of(concept));
		UUID retiredProgram = UUID.randomUUID();
		when(paymentRateRepository.findActiveByConceptId(conceptId))
				.thenReturn(List.of(new PaymentRate(conceptId, retiredProgram, null, BigDecimal.valueOf(500), null,
						LocalDateTime.now())));

		useCase.updatePaymentConcept(quotaCommand(conceptId, 1));

		verify(coverageChecker).requireCompleteCoverage(Set.of(retiredProgram));
	}

	/**
	 * Editing something unrelated on a concept that is already a quota must not
	 * re-run the coverage check: its rates are untouched by this operation, so
	 * re-reading them would only add a query that can fail for reasons outside
	 * the caller's control.
	 */
	@Test
	void updatePaymentConcept_doesNotRecheckCoverageWhenTheConceptWasAlreadyAQuota() {
		PaymentConcept quota = new PaymentConcept("Cuota", "COD-CUOTA", "Descripcion", "Politicas",
				PaymentConceptType.PERIODIC_QUOTA, 2, false, 1, 2, true, null, null);
		ReflectionTestUtils.setField(quota, "id", conceptId);
		when(paymentConceptRepository.findById(conceptId)).thenReturn(Optional.of(quota));
		when(paymentConceptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		useCase.updatePaymentConcept(quotaCommand(conceptId, 2));

		verify(paymentRateRepository, never()).findActiveByConceptId(any());
	}

	/**
	 * Not being a quota means the completeness rule does not apply, so the rate
	 * set is not even read.
	 */
	@Test
	void updatePaymentConcept_doesNotReadRatesWhenTheTypeStaysEnrollment() {
		when(paymentConceptRepository.findById(conceptId)).thenReturn(Optional.of(concept));
		when(paymentConceptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		useCase.updatePaymentConcept(commandWith(conceptId, "Reinscripcion", 3, 4, null, null));

		verify(paymentRateRepository, never()).findActiveByConceptId(any());
	}
}
