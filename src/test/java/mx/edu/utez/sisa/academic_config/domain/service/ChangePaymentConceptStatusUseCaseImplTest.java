package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangePaymentConceptStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentConceptResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository.AcademicProgramReference;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentRateRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentQuotaLevelException;
import mx.edu.utez.sisa.academic_config.shared.exception.IncompletePaymentRateSetException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangePaymentConceptStatusUseCaseImplTest {

	@Mock
	private PaymentConceptRepository paymentConceptRepository;

	@Mock
	private PaymentRateRepository paymentRateRepository;

	@Mock
	private AcademicProgramRepository programRepository;

	private ChangePaymentConceptStatusUseCaseImpl useCase;

	private PaymentConcept concept;
	private UUID conceptId;
	private UUID quotaId;

	@BeforeEach
	void setUp() {
		useCase = new ChangePaymentConceptStatusUseCaseImpl(paymentConceptRepository, paymentRateRepository,
				new PaymentQuotaCoverageChecker(programRepository));
		concept = new PaymentConcept("Inscripcion", "COD-1", "Descripcion", "Politicas", PaymentConceptType.ENROLLMENT,
				null, false, 1, 2, true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
		conceptId = UUID.randomUUID();
		quotaId = UUID.randomUUID();
		ReflectionTestUtils.setField(concept, "id", conceptId);
	}

	@Test
	void changeStatus_deactivatesAnActiveConcept() {
		when(paymentConceptRepository.findById(conceptId)).thenReturn(Optional.of(concept));
		when(paymentConceptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentConceptResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), conceptId, PaymentConceptStatus.INACTIVE));

		assertThat(result.status()).isEqualTo(PaymentConceptStatus.INACTIVE);
	}

	@Test
	void changeStatus_reactivatesAnInactiveConcept() {
		concept.deactivate();
		when(paymentConceptRepository.findById(conceptId)).thenReturn(Optional.of(concept));
		when(paymentConceptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentConceptResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), conceptId, PaymentConceptStatus.ACTIVE));

		assertThat(result.status()).isEqualTo(PaymentConceptStatus.ACTIVE);
	}

	@Test
	void changeStatus_isIdempotentWhenTargetMatchesCurrentStatus() {
		when(paymentConceptRepository.findById(conceptId)).thenReturn(Optional.of(concept));
		when(paymentConceptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentConceptResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), conceptId, PaymentConceptStatus.ACTIVE));

		assertThat(result.status()).isEqualTo(PaymentConceptStatus.ACTIVE);
	}

	@Test
	void changeStatus_rejectsUnknownPaymentConceptId() {
		UUID unknownId = UUID.randomUUID();
		when(paymentConceptRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), unknownId, PaymentConceptStatus.ACTIVE)))
				.isInstanceOf(PaymentConceptNotFoundException.class);
	}

	/**
	 * While this concept was inactive, another one took the same level. Bringing
	 * this one back would put two active prices on one student, which is exactly
	 * what the level rule exists to prevent — so the level is re-checked here
	 * rather than trusted from when the concept was deactivated.
	 */
	@Test
	void changeStatus_refusesToActivateAQuotaWhoseLevelWasTakenWhileItWasInactive() {
		PaymentConcept quota = quotaConcept();
		quota.deactivate();
		when(paymentConceptRepository.findById(quotaId)).thenReturn(Optional.of(quota));
		when(paymentConceptRepository.findActiveByTypeAndLevelNumber(PaymentConceptType.PERIODIC_QUOTA, 1, quotaId))
				.thenReturn(Optional.of(concept));

		assertThatThrownBy(() -> useCase.changeStatus(
				new ChangeStatusCommand(UUID.randomUUID(), quotaId, PaymentConceptStatus.ACTIVE)))
				.isInstanceOf(DuplicatePaymentQuotaLevelException.class);

		verify(paymentConceptRepository, never()).save(any());
	}

	/**
	 * A career created while the quota was inactive has no price. Reactivating it
	 * as-is would leave that student under a quota concept that prices nothing
	 * for their career, which is the state the completeness rule exists to prevent
	 * — and the rate endpoint cannot be reached while the concept is inactive,
	 * because the level check would pass but there is nothing to reconcile against.
	 */
	@Test
	void changeStatus_refusesToActivateAQuotaThatNoLongerPricesEveryActiveCareer() {
		PaymentConcept quota = quotaConcept();
		quota.deactivate();
		UUID priced = UUID.randomUUID();
		UUID unpriced = UUID.randomUUID();
		when(paymentConceptRepository.findById(quotaId)).thenReturn(Optional.of(quota));
		when(paymentConceptRepository.findActiveByTypeAndLevelNumber(PaymentConceptType.PERIODIC_QUOTA, 1, quotaId))
				.thenReturn(Optional.empty());
		when(paymentRateRepository.findActiveByConceptId(quotaId)).thenReturn(List.of(
				new PaymentRate(quotaId, priced, null, BigDecimal.valueOf(500), null, LocalDateTime.now())));
		when(programRepository.findAllActive())
				.thenReturn(List.of(new AcademicProgramReference(priced, "Software"),
						new AcademicProgramReference(unpriced, "Civil")));

		assertThatThrownBy(() -> useCase.changeStatus(
				new ChangeStatusCommand(UUID.randomUUID(), quotaId, PaymentConceptStatus.ACTIVE)))
				.isInstanceOf(IncompletePaymentRateSetException.class)
				.hasMessageContaining("Civil");

		verify(paymentConceptRepository, never()).save(any());
	}

	@Test
	void changeStatus_activatesACompleteQuota() {
		PaymentConcept quota = quotaConcept();
		quota.deactivate();
		UUID priced = UUID.randomUUID();
		when(paymentConceptRepository.findById(quotaId)).thenReturn(Optional.of(quota));
		when(paymentConceptRepository.findActiveByTypeAndLevelNumber(PaymentConceptType.PERIODIC_QUOTA, 1, quotaId))
				.thenReturn(Optional.empty());
		when(paymentRateRepository.findActiveByConceptId(quotaId)).thenReturn(List.of(
				new PaymentRate(quotaId, priced, null, BigDecimal.valueOf(500), null, LocalDateTime.now())));
		when(programRepository.findAllActive())
				.thenReturn(List.of(new AcademicProgramReference(priced, "Software")));
		when(paymentConceptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentConceptResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), quotaId, PaymentConceptStatus.ACTIVE));

		assertThat(result.status()).isEqualTo(PaymentConceptStatus.ACTIVE);
	}

	/**
	 * The checks above must not block the way out of a broken concept. Refusing to
	 * deactivate would leave an operator with a quota they cannot fix and cannot
	 * remove.
	 */
	@Test
	void changeStatus_deactivationNeverRunsTheActivationChecks() {
		PaymentConcept quota = quotaConcept();
		when(paymentConceptRepository.findById(quotaId)).thenReturn(Optional.of(quota));
		when(paymentConceptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentConceptResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), quotaId, PaymentConceptStatus.INACTIVE));

		assertThat(result.status()).isEqualTo(PaymentConceptStatus.INACTIVE);
		verify(paymentConceptRepository, never()).findActiveByTypeAndLevelNumber(any(), any(), any());
		verify(paymentRateRepository, never()).findActiveByConceptId(any());
	}

	private PaymentConcept quotaConcept() {
		PaymentConcept quota = new PaymentConcept("Cuota primer", "CUA-1", null, null,
				PaymentConceptType.PERIODIC_QUOTA, 1, true, null, null, false, null, null);
		ReflectionTestUtils.setField(quota, "id", quotaId);
		return quota;
	}
}