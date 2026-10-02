package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRateStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentRateDraft;
import mx.edu.utez.sisa.academic_config.domain.port.in.ReconcilePaymentRatesUseCase.PaymentRateResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.ReconcilePaymentRatesUseCase.ReconcilePaymentRatesCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository.AcademicProgramReference;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentRateRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentRateException;
import mx.edu.utez.sisa.academic_config.shared.exception.IncompletePaymentRateSetException;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the whole-set reconciliation, which replaced the per-rate
 * {@code SetPaymentRateUseCase}. The tests that matter most are the ones about
 * what is NOT written: the completeness and reference checks run before any save,
 * because a rejected set that had already written four of nine rows would leave
 * the catalog in a state no user asked for and no single request can describe.
 */
@ExtendWith(MockitoExtension.class)
class ReconcilePaymentRatesUseCaseImplTest {

	private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

	private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 3, 1, 10, 0);

	@Mock
	private PaymentRateRepository paymentRateRepository;

	@Mock
	private PaymentConceptRepository paymentConceptRepository;

	@Mock
	private AcademicProgramRepository programRepository;

	@Mock
	private AcademicPeriodRepository periodRepository;

	private ReconcilePaymentRatesUseCaseImpl useCase;

	private UUID conceptId;

	private UUID programId;

	private UUID periodId;

	@BeforeEach
	void setUp() {
		// The real checker over the mocked program repository, not a mock of the
		// checker: the completeness rejections below are the behaviour under test,
		// and stubbing the checker would make them pass without anything checking.
		useCase = new ReconcilePaymentRatesUseCaseImpl(paymentRateRepository, paymentConceptRepository,
				programRepository, periodRepository, new PaymentQuotaCoverageChecker(programRepository),
				Clock.fixed(NOW, ZoneOffset.UTC));
		conceptId = UUID.randomUUID();
		programId = UUID.randomUUID();
		periodId = UUID.randomUUID();
	}

	@Test
	void reconcile_rejectsNonExistentConceptId() {
		when(paymentConceptRepository.findById(conceptId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.reconcileRates(command(continuousDraft(programId, "100"))))
				.isInstanceOf(PaymentConceptReferenceNotFoundException.class);

		verify(paymentRateRepository, never()).save(any());
	}

	@Test
	void reconcile_rejectsMissingConceptId() {
		assertThatThrownBy(() -> useCase.reconcileRates(new ReconcilePaymentRatesCommand(null, List.of())))
				.isInstanceOf(PaymentConceptReferenceNotFoundException.class);
	}

	@Test
	void reconcile_rejectsNonExistentProgramId() {
		givenConcept(newConcept(PaymentConceptType.ENROLLMENT));
		when(programRepository.findById(programId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.reconcileRates(command(continuousDraft(programId, "100"))))
				.isInstanceOf(ProgramNotFoundException.class);

		verify(paymentRateRepository, never()).save(any());
	}

	/**
	 * The period reference is validated even though nothing else in the flow reads
	 * the period: an unresolvable {@code periodId} is a dangling price that the
	 * catalog would keep forever and nothing would ever resolve.
	 */
	@Test
	void reconcile_rejectsNonExistentPeriodId() {
		givenConcept(newConcept(PaymentConceptType.ENROLLMENT));
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		when(periodRepository.findById(periodId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.reconcileRates(
				command(new PaymentRateDraft(programId, AcademicLevel.LICENCIATURA, BigDecimal.valueOf(100), periodId))))
				.isInstanceOf(PeriodNotFoundException.class);

		verify(paymentRateRepository, never()).save(any());
	}

	@Test
	void reconcile_rejectsZeroAmount() {
		givenConcept(newConcept(PaymentConceptType.ENROLLMENT));

		assertThatThrownBy(() -> useCase.reconcileRates(command(continuousDraft(programId, "0"))))
				.isInstanceOf(InvalidPaymentRateDataException.class);

		verify(paymentRateRepository, never()).save(any());
	}

	@Test
	void reconcile_rejectsNegativeAmount() {
		givenConcept(newConcept(PaymentConceptType.ENROLLMENT));

		assertThatThrownBy(() -> useCase.reconcileRates(command(continuousDraft(programId, "-10"))))
				.isInstanceOf(InvalidPaymentRateDataException.class);
	}

	/**
	 * A null amount is rejected as data, not treated as zero, because "the editor
	 * sent no amount" and "the editor sent zero" are different bugs and the second
	 * one is a silent free charge.
	 */
	@Test
	void reconcile_rejectsNullAmount() {
		givenConcept(newConcept(PaymentConceptType.ENROLLMENT));

		assertThatThrownBy(() -> useCase.reconcileRates(
				command(new PaymentRateDraft(programId, AcademicLevel.LICENCIATURA, null, null))))
				.isInstanceOf(InvalidPaymentRateDataException.class);
	}

	/**
	 * Two rows for the same destination in one payload would leave the outcome
	 * dependent on iteration order, so the set is rejected instead of
	 * last-one-wins.
	 */
	@Test
	void reconcile_rejectsTheSameDestinationTwice() {
		givenConcept(newConcept(PaymentConceptType.ENROLLMENT));

		assertThatThrownBy(() -> useCase.reconcileRates(
				command(continuousDraft(programId, "100"), continuousDraft(programId, "200"))))
				.isInstanceOf(DuplicatePaymentRateException.class);

		verify(paymentRateRepository, never()).save(any());
	}

	@Test
	void reconcile_changedAmountDeactivatesTheSupersededRowAndInsertsANewOne() {
		givenConcept(newConcept(PaymentConceptType.ENROLLMENT));
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		PaymentRate existing = new PaymentRate(conceptId, programId, AcademicLevel.LICENCIATURA,
				BigDecimal.valueOf(1000), null, CREATED_AT.minusDays(1));
		when(paymentRateRepository.findActive(conceptId, programId, AcademicLevel.LICENCIATURA, null))
				.thenReturn(Optional.of(existing));
		when(paymentRateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(paymentRateRepository.findHistoryByConceptId(conceptId)).thenReturn(List.of(existing));

		useCase.reconcileRates(command(continuousDraft(programId, "1500")));

		// The superseded row is deactivated in place, not re-saved: inside the
		// transaction it is a managed entity, so JPA flushes the status change.
		// Asserting the field rather than a save() call is what pins that.
		assertThat(existing.getStatus()).isEqualTo(PaymentRateStatus.INACTIVE);

		// Exactly one write: the replacement. A second save of the old row would
		// be redundant with dirty checking and would mask the case where the
		// implementation forgot to deactivate it at all.
		ArgumentCaptor<PaymentRate> captor = ArgumentCaptor.forClass(PaymentRate.class);
		verify(paymentRateRepository, times(1)).save(captor.capture());
		PaymentRate inserted = captor.getValue();
		assertThat(inserted.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500));
		assertThat(inserted.getStatus()).isEqualTo(PaymentRateStatus.ACTIVE);
		assertThat(inserted.getCreatedAt()).isEqualTo(CREATED_AT);
	}

	/**
	 * The point of the whole redesign: saving the same price again writes nothing.
	 * Manufacturing a new row here would make {@code createdAt} mean "whenever
	 * anyone hit save" instead of "when this price changed", which is the only
	 * question the history is answerable for.
	 */
	@Test
	void reconcile_unchangedAmountKeepsTheExistingRowAndWritesNothing() {
		givenConcept(newConcept(PaymentConceptType.ENROLLMENT));
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		PaymentRate existing = new PaymentRate(conceptId, programId, AcademicLevel.LICENCIATURA,
				BigDecimal.valueOf(1000), null, CREATED_AT.minusDays(30));
		when(paymentRateRepository.findActive(conceptId, programId, AcademicLevel.LICENCIATURA, null))
				.thenReturn(Optional.of(existing));
		when(paymentRateRepository.findHistoryByConceptId(conceptId)).thenReturn(List.of(existing));

		List<PaymentRateResult> results = useCase.reconcileRates(command(continuousDraft(programId, "1000")));

		assertThat(results).hasSize(1);
		assertThat(results.get(0).id()).isEqualTo(existing.getId());
		assertThat(existing.getStatus()).isEqualTo(PaymentRateStatus.ACTIVE);
		verify(paymentRateRepository, never()).save(any());
	}

	@Test
	void reconcile_aDestinationTheCallerOmittedIsDeactivatedRatherThanDeleted() {
		givenConcept(newConcept(PaymentConceptType.ENROLLMENT));
		PaymentRate withdrawn = new PaymentRate(conceptId, programId, AcademicLevel.LICENCIATURA,
				BigDecimal.valueOf(800), null, CREATED_AT.minusDays(10));
		when(paymentRateRepository.findHistoryByConceptId(conceptId)).thenReturn(List.of(withdrawn));
		when(paymentRateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		useCase.reconcileRates(command());

		assertThat(withdrawn.getStatus()).isEqualTo(PaymentRateStatus.INACTIVE);
	}

	@Test
	void reconcile_alreadyInactiveRowsAreLeftUntouched() {
		givenConcept(newConcept(PaymentConceptType.ENROLLMENT));
		PaymentRate old = new PaymentRate(conceptId, programId, AcademicLevel.LICENCIATURA, BigDecimal.valueOf(300),
				null, CREATED_AT.minusYears(1));
		old.deactivate();
		when(paymentRateRepository.findHistoryByConceptId(conceptId)).thenReturn(List.of(old));

		useCase.reconcileRates(command());

		verify(paymentRateRepository, never()).save(any());
	}

	@Test
	void reconcile_aPeriodicQuotaRejectsAPartialSet() {
		UUID otherProgram = UUID.randomUUID();
		givenConcept(newConcept(PaymentConceptType.PERIODIC_QUOTA, 1));
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		when(programRepository.findAllActive()).thenReturn(
				List.of(reference(programId, "Software"), reference(otherProgram, "Civil")));

		assertThatThrownBy(() -> useCase.reconcileRates(command(quotaDraft(programId, "100"))))
				.isInstanceOf(IncompletePaymentRateSetException.class)
				.hasMessageContaining("Civil");

		verify(paymentRateRepository, never()).save(any());
	}

	@Test
	void reconcile_aPeriodicQuotaAcceptsTheExactActiveSet() {
		UUID otherProgram = UUID.randomUUID();
		givenConcept(newConcept(PaymentConceptType.PERIODIC_QUOTA, 1));
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		when(programRepository.findById(otherProgram)).thenReturn(Optional.of(newProgram()));
		when(programRepository.findAllActive()).thenReturn(
				List.of(reference(programId, "Software"), reference(otherProgram, "Civil")));
		when(paymentRateRepository.findActive(any(), any(), any(), any())).thenReturn(Optional.empty());
		when(paymentRateRepository.findHistoryByConceptId(conceptId)).thenReturn(List.of());
		when(paymentRateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		List<PaymentRateResult> results = useCase
				.reconcileRates(command(quotaDraft(programId, "100"), quotaDraft(otherProgram, "200")));

		assertThat(results).hasSize(2);
		assertThat(results).allMatch(rate -> rate.level() == null && rate.periodId() == null);
	}

	/**
	 * A career that was deactivated while nobody was looking. Its row would
	 * otherwise sit in the catalog as a price no active career can use and no user
	 * is allowed to remove, so the set is refused rather than accepted as
	 * "complete plus one extra".
	 */
	@Test
	void reconcile_aPeriodicQuotaRejectsAPriceForANoLongerActiveProgram() {
		UUID inactiveProgram = UUID.randomUUID();
		givenConcept(newConcept(PaymentConceptType.PERIODIC_QUOTA, 1));
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		when(programRepository.findById(inactiveProgram)).thenReturn(Optional.of(newProgram()));
		when(programRepository.findAllActive()).thenReturn(List.of(reference(programId, "Software")));

		assertThatThrownBy(() -> useCase.reconcileRates(
				command(quotaDraft(programId, "100"), quotaDraft(inactiveProgram, "150"))))
				.isInstanceOf(InvalidPaymentRateDataException.class)
				.hasMessageContaining("not active");
	}

	@Test
	void reconcile_aPeriodicQuotaRejectsAPeriodScopedRate() {
		givenConcept(newConcept(PaymentConceptType.PERIODIC_QUOTA, 1));
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		when(periodRepository.findById(periodId)).thenReturn(Optional.of(newPeriod()));

		assertThatThrownBy(() -> useCase.reconcileRates(
				command(new PaymentRateDraft(programId, null, BigDecimal.valueOf(100), periodId))))
				.isInstanceOf(InvalidPaymentRateDataException.class)
				.hasMessageContaining("cannot be scoped to a single period");
	}

	/**
	 * A recurring quota's level lives on the concept. A rate carrying its own level
	 * would price a different rung of the ladder than the one the pricing lookups
	 * read, and would be silently unreachable.
	 */
	@Test
	void reconcile_aPeriodicQuotaRejectsARateCarryingItsOwnLevel() {
		givenConcept(newConcept(PaymentConceptType.PERIODIC_QUOTA, 1));
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));

		assertThatThrownBy(() -> useCase.reconcileRates(
				command(new PaymentRateDraft(programId, AcademicLevel.LICENCIATURA, BigDecimal.valueOf(100), null))))
				.isInstanceOf(InvalidPaymentRateDataException.class)
				.hasMessageContaining("priced per program only");
	}

	@Test
	void reconcile_aPeriodicQuotaRejectsARateWithNoProgram() {
		givenConcept(newConcept(PaymentConceptType.PERIODIC_QUOTA, 1));

		assertThatThrownBy(() -> useCase.reconcileRates(command(new PaymentRateDraft(null, null, BigDecimal.valueOf(100), null))))
				.isInstanceOf(InvalidPaymentRateDataException.class)
				.hasMessageContaining("must name the program it prices");
	}

	@Test
	void reconcile_aPeriodScopedRateDoesNotCloseTheContinuousRateForTheSameProgram() {
		givenConcept(newConcept(PaymentConceptType.ENROLLMENT));
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		when(periodRepository.findById(periodId)).thenReturn(Optional.of(newPeriod()));
		when(paymentRateRepository.findActive(conceptId, programId, AcademicLevel.LICENCIATURA, periodId))
				.thenReturn(Optional.empty());
		when(paymentRateRepository.findHistoryByConceptId(conceptId)).thenReturn(List.of());
		when(paymentRateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		useCase.reconcileRates(
				command(new PaymentRateDraft(programId, AcademicLevel.LICENCIATURA, BigDecimal.valueOf(2000), periodId)));

		verify(paymentRateRepository, never()).findActive(conceptId, programId, AcademicLevel.LICENCIATURA, null);
		verify(paymentRateRepository, times(1)).save(any());
	}

	@Test
	void reconcile_allowsNullProgramIdAndLevelForAConceptThatIsNotAQuota() {
		givenConcept(newConcept(PaymentConceptType.ENROLLMENT));
		when(paymentRateRepository.findActive(conceptId, null, null, null)).thenReturn(Optional.empty());
		when(paymentRateRepository.findHistoryByConceptId(conceptId)).thenReturn(List.of());
		when(paymentRateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		List<PaymentRateResult> results = useCase
				.reconcileRates(command(new PaymentRateDraft(null, null, BigDecimal.valueOf(500), null)));

		assertThat(results).hasSize(1);
		assertThat(results.get(0).programId()).isNull();
		assertThat(results.get(0).level()).isNull();
	}

	@Test
	void reconcile_successfulCreationReturnsTheResultWithStatusAndTimestamp() {
		givenConcept(newConcept(PaymentConceptType.ENROLLMENT));
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		when(paymentRateRepository.findActive(conceptId, programId, AcademicLevel.LICENCIATURA, null))
				.thenReturn(Optional.empty());
		when(paymentRateRepository.findHistoryByConceptId(conceptId)).thenReturn(List.of());
		when(paymentRateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		List<PaymentRateResult> results = useCase.reconcileRates(command(continuousDraft(programId, "1500")));

		assertThat(results).hasSize(1);
		PaymentRateResult result = results.get(0);
		assertThat(result.conceptId()).isEqualTo(conceptId);
		assertThat(result.programId()).isEqualTo(programId);
		assertThat(result.level()).isEqualTo(AcademicLevel.LICENCIATURA);
		assertThat(result.amount()).isEqualByComparingTo(BigDecimal.valueOf(1500));
		assertThat(result.periodId()).isNull();
		assertThat(result.status()).isEqualTo(PaymentRateStatus.ACTIVE);
		assertThat(result.createdAt()).isEqualTo(CREATED_AT);
	}

	/**
	 * Stubs the concept lookup AND pins the concept's id to this test's
	 * {@code conceptId}. The implementation reads the id off the concept it got
	 * back rather than off the command when it lists history, so a concept that
	 * kept its own generated id would send {@code findHistoryByConceptId} an
	 * argument no stub matches — which Mockito reports as a strict-stubbing
	 * mismatch rather than as the empty result it actually produced.
	 */
	private void givenConcept(PaymentConcept concept) {
		// The implementation lists history with concept.getId(), not with the id in
		// the command, so the concept has to answer with this test's conceptId.
		// A domain object built in memory has no id, hence the spy.
		PaymentConcept identified = spy(concept);
		lenient().when(identified.getId()).thenReturn(conceptId);
		lenient().when(paymentConceptRepository.findById(conceptId)).thenReturn(Optional.of(identified));
	}

	private ReconcilePaymentRatesCommand command(PaymentRateDraft... drafts) {
		return new ReconcilePaymentRatesCommand(conceptId, List.of(drafts));
	}

	private static PaymentRateDraft continuousDraft(UUID program, String amount) {
		return new PaymentRateDraft(program, AcademicLevel.LICENCIATURA, new BigDecimal(amount), null);
	}

	private static PaymentRateDraft quotaDraft(UUID program, String amount) {
		return new PaymentRateDraft(program, null, new BigDecimal(amount), null);
	}

	private static AcademicProgramReference reference(UUID id, String name) {
		return new AcademicProgramReference(id, name);
	}

	private static PaymentConcept newConcept(PaymentConceptType type) {
		return newConcept(type, null);
	}

	private static PaymentConcept newConcept(PaymentConceptType type, Integer levelNumber) {
		return new PaymentConcept("Concepto", "COD-1", null, null, type, levelNumber, true, null, null, false, null,
				null);
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