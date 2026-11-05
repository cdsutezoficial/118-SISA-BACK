package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionQuotaPort;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateNotificationPort;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.CashAmountChangedException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigCapacityReachedException;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.finance.domain.port.out.CheckoutAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmAdmissionPaymentUseCaseImplTest {

	private static final BigDecimal AMOUNT = new BigDecimal("500.00");

	private static final UUID CHARGE_ID = UUID.randomUUID();

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private FichaPaymentPort fichaPaymentPort;

	@Mock
	private CheckoutAttemptRepository checkoutAttemptRepository;

	@Mock
	private CandidateNotificationPort notifications;

	@Mock
	private AdmissionQuotaPort admissionQuotaPort;

	private ConfirmAdmissionPaymentUseCaseImpl useCase;

	private UUID candidateId;
	private Candidate candidate;

	@BeforeEach
	void setUp() {
		useCase = new ConfirmAdmissionPaymentUseCaseImpl(candidateRepository, fichaPaymentPort,
				checkoutAttemptRepository, notifications, admissionQuotaPort,
				Clock.fixed(Instant.parse("2026-11-05T17:00:00Z"), ZoneOffset.UTC), 60);
		candidateId = UUID.randomUUID();
		candidate = new Candidate(UUID.randomUUID(), UUID.randomUUID(), "ADM-2026-000001", true, true, null);
		ReflectionTestUtils.setField(candidate, "id", candidateId);
		candidate.linkFichaPayment(CHARGE_ID);
	}

	private void stubHappyPath() {
		when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
		when(fichaPaymentPort.findCharge(CHARGE_ID)).thenReturn(Optional.of(FichaTestData.pendingCharge(CHARGE_ID)));
		when(candidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(fichaPaymentPort.settle(any())).thenReturn(new FichaPaymentPort.SettledFicha(AMOUNT,
				BigDecimal.ZERO.setScale(2), AMOUNT, AMOUNT, null, false));
	}

	@Test
	void confirm_marksPaymentAndCandidatePaidWithReceipt_andBooksTheSettledAmount() {
		stubHappyPath();

		var result = useCase.confirm(candidateId, null);

		assertThat(result.candidateStatus()).isEqualTo(CandidateStatus.PAID);
		assertThat(result.receiptNumber()).isNotNull().startsWith("REC-");
		assertThat(result.amount()).isEqualByComparingTo(AMOUNT);
		assertThat(result.paidAt()).isNotNull();
		assertThat(result.referenceNumber()).isEqualTo(FichaTestData.REFERENCE);
		ArgumentCaptor<FichaPaymentPort.SettleFichaCommand> settled = ArgumentCaptor
				.forClass(FichaPaymentPort.SettleFichaCommand.class);
		verify(fichaPaymentPort).settle(settled.capture());
		assertThat(settled.getValue().receiptNumber()).isEqualTo(result.receiptNumber());

		ArgumentCaptor<Candidate> candidateCaptor = ArgumentCaptor.forClass(Candidate.class);
		verify(candidateRepository).save(candidateCaptor.capture());
		assertThat(candidateCaptor.getValue().getStatus()).isEqualTo(CandidateStatus.PAID);
		assertThat(candidateCaptor.getValue().getPaidAt()).isNotNull();
	}

	// ── F1: cobro en efectivo ─────────────────────────────────────────────────

	@Test
	void confirmCash_booksCashWithTheCollectorAndMarksTheCandidatePaid() {
		stubHappyPath();
		UUID cashier = UUID.randomUUID();
		when(admissionQuotaPort.lockQuota(candidate.getAdmissionConfigId()))
				.thenReturn(new AdmissionQuotaPort.QuotaState(10));
		when(admissionQuotaPort.countOccupiedExcluding(eq(candidate.getAdmissionConfigId()), eq(candidateId), any()))
				.thenReturn(9L);

		var result = useCase.confirmCash(candidateId, AMOUNT, cashier, "Ana Caja");

		assertThat(result.candidateStatus()).isEqualTo(CandidateStatus.PAID);
		assertThat(result.receiptNumber()).startsWith("REC-");
		ArgumentCaptor<FichaPaymentPort.SettleFichaCommand> command = ArgumentCaptor
				.forClass(FichaPaymentPort.SettleFichaCommand.class);
		verify(fichaPaymentPort).settle(command.capture());
		assertThat(command.getValue().online()).isFalse();
		assertThat(command.getValue().externalOrderId()).isNull();
		assertThat(command.getValue().collectedById()).isEqualTo(cashier);
		assertThat(command.getValue().collectedByName()).isEqualTo("Ana Caja");
		verify(notifications).paymentConfirmed(result);
	}

	@Test
	void confirmCash_refusesAFullCareerBeforeBookingAnything() {
		when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
		when(fichaPaymentPort.findCharge(CHARGE_ID)).thenReturn(Optional.of(FichaTestData.pendingCharge(CHARGE_ID)));
		when(admissionQuotaPort.lockQuota(candidate.getAdmissionConfigId()))
				.thenReturn(new AdmissionQuotaPort.QuotaState(10));
		when(admissionQuotaPort.countOccupiedExcluding(eq(candidate.getAdmissionConfigId()), eq(candidateId), any()))
				.thenReturn(10L);

		assertThatThrownBy(() -> useCase.confirmCash(candidateId, AMOUNT, UUID.randomUUID(), "Caja"))
				.isInstanceOf(ProgramAdmissionConfigCapacityReachedException.class);
		verify(fichaPaymentPort, never()).settle(any());
		verify(candidateRepository, never()).save(any());
	}

	@Test
	void confirmCash_refusesWhenTheAmountChanged_andSendsNoReceipt() {
		stubHappyPath();
		when(admissionQuotaPort.lockQuota(candidate.getAdmissionConfigId()))
				.thenReturn(new AdmissionQuotaPort.QuotaState(10));

		assertThatThrownBy(() -> useCase.confirmCash(candidateId, new BigDecimal("450.00"), UUID.randomUUID(), "Caja"))
				.isInstanceOf(CashAmountChangedException.class);
		verify(notifications, never()).paymentConfirmed(any());
	}

	@Test
	void confirmCash_ofADifference_doesNotCompeteForAPlace() {
		UUID difference = UUID.randomUUID();
		ReflectionTestUtils.setField(candidate, "programChangePaymentId", difference);
		when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
		when(fichaPaymentPort.findCharge(difference)).thenReturn(Optional.of(FichaTestData.pendingCharge(difference)));
		when(candidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(fichaPaymentPort.settle(any())).thenReturn(new FichaPaymentPort.SettledFicha(AMOUNT,
				BigDecimal.ZERO.setScale(2), AMOUNT, AMOUNT, null, false));

		var result = useCase.confirmCash(candidateId, AMOUNT, UUID.randomUUID(), "Caja");

		assertThat(result.programChange()).isTrue();
		verify(admissionQuotaPort, never()).lockQuota(any());
	}

	@Test
	void confirm_withoutAttempt_letsFinancePriceLive() {
		stubHappyPath();

		useCase.confirm(candidateId, null);

		ArgumentCaptor<FichaPaymentPort.SettleFichaCommand> command = ArgumentCaptor
				.forClass(FichaPaymentPort.SettleFichaCommand.class);
		verify(fichaPaymentPort).settle(command.capture());
		assertThat(command.getValue().financePaymentId()).isEqualTo(CHARGE_ID);
		assertThat(command.getValue().charged()).isNull();
		assertThat(command.getValue().online()).isFalse();
		verify(checkoutAttemptRepository, never()).findByOrderId(any());
	}

	/**
	 * The ficha was re-quoted by a second checkout (discount granted in
	 * between), but the bank captured the FIRST attempt: the numbers booked are
	 * the captured attempt's snapshot, not the latest quote.
	 */
	@Test
	void confirm_withCapturedAttempt_settlesWithThatAttemptsSnapshot() {
		stubHappyPath();
		UUID benefitId = UUID.randomUUID();
		UUID rateId = UUID.randomUUID();
		CheckoutAttempt first = new CheckoutAttempt("ORD-1", CHARGE_ID, candidateId, null, new BigDecimal("400.00"),
				Instant.now(), rateId, AMOUNT, new BigDecimal("100.00"), benefitId);
		when(checkoutAttemptRepository.findByOrderId("ORD-1")).thenReturn(Optional.of(first));

		useCase.confirm(candidateId, "ORD-1");

		ArgumentCaptor<FichaPaymentPort.SettleFichaCommand> command = ArgumentCaptor
				.forClass(FichaPaymentPort.SettleFichaCommand.class);
		verify(fichaPaymentPort).settle(command.capture());
		FichaPaymentPort.ChargedAmounts charged = command.getValue().charged();
		assertThat(charged.amountDue()).isEqualByComparingTo("400.00");
		assertThat(charged.originalAmount()).isEqualByComparingTo(AMOUNT);
		assertThat(charged.discountAmount()).isEqualByComparingTo("100.00");
		assertThat(charged.benefitId()).isEqualTo(benefitId);
		assertThat(charged.rateId()).isEqualTo(rateId);
		assertThat(command.getValue().online()).isTrue();
		assertThat(command.getValue().externalOrderId()).isEqualTo("ORD-1");
	}

	@Test
	void confirm_unknownCandidateThrowsNotFound() {
		when(candidateRepository.findById(candidateId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.confirm(candidateId, null)).isInstanceOf(CandidateNotFoundException.class);
	}

	@Test
	void confirm_alreadyPaidThrowsConflict_andBooksNothing() {
		candidate.markPaid();
		when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
		when(fichaPaymentPort.findCharge(CHARGE_ID)).thenReturn(Optional.of(FichaTestData.paidCharge(CHARGE_ID)));

		assertThatThrownBy(() -> useCase.confirm(candidateId, null)).isInstanceOf(CandidateAlreadyPaidException.class);
		verify(fichaPaymentPort, never()).settle(any());
	}

	@Test
	void confirm_aCareerChangeDifferenceMakesTheNewChargeTheFicha() {
		UUID differenceId = UUID.randomUUID();
		candidate.markPaid();
		candidate.awaitProgramChangePayment(differenceId);
		when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
		when(fichaPaymentPort.findCharge(differenceId))
				.thenReturn(Optional.of(FichaTestData.pendingCharge(differenceId)));
		when(candidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(fichaPaymentPort.settle(any())).thenReturn(new FichaPaymentPort.SettledFicha(AMOUNT,
				BigDecimal.ZERO.setScale(2), AMOUNT, AMOUNT, null, false));

		var result = useCase.confirm(candidateId, null);

		ArgumentCaptor<FichaPaymentPort.SettleFichaCommand> settled = ArgumentCaptor
				.forClass(FichaPaymentPort.SettleFichaCommand.class);
		verify(fichaPaymentPort).settle(settled.capture());
		assertThat(settled.getValue().financePaymentId()).isEqualTo(differenceId);
		assertThat(result.receiptNumber()).endsWith("-C");
		assertThat(candidate.getFichaPaymentId()).isEqualTo(differenceId);
		assertThat(candidate.getProgramChangePaymentId()).isNull();
		assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.PAID);
	}

	/**
	 * E6: paying a $100 difference on a $1,850 career whose earlier ficha brought $1,750
	 * reports $100 paid now, the $1,850 total and the $1,750 with the earlier receipt.
	 */
	@Test
	void confirm_aDifferenceReportsWhatWasPaidNowApartFromTheTotal() {
		UUID differenceId = UUID.randomUUID();
		candidate.markPaid();
		candidate.awaitProgramChangePayment(differenceId);
		when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
		when(fichaPaymentPort.findCharge(differenceId)).thenReturn(Optional.of(
				FichaTestData.partlyPaidCharge(differenceId, new BigDecimal("1750.00"))));
		when(fichaPaymentPort.findCharge(CHARGE_ID)).thenReturn(Optional.of(FichaTestData.paidCharge(CHARGE_ID)));
		when(candidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		BigDecimal total = new BigDecimal("1850.00");
		when(fichaPaymentPort.settle(any())).thenReturn(new FichaPaymentPort.SettledFicha(total,
				BigDecimal.ZERO.setScale(2), total, total, null, false));

		var result = useCase.confirm(candidateId, null);

		assertThat(result.amount()).isEqualByComparingTo("100.00");
		assertThat(result.total()).isEqualByComparingTo("1850.00");
		assertThat(result.previouslyPaid()).isEqualByComparingTo("1750.00");
		assertThat(result.previousReceipt()).isEqualTo("REC-20260922-000001");
		assertThat(result.previousPaidAt()).isNotNull();
		assertThat(result.programChange()).isTrue();
	}

	/** F3: the receipt is announced by the confirmation, whichever path called it. */
	@Test
	void confirm_announcesTheReceipt() {
		stubHappyPath();

		var result = useCase.confirm(candidateId, null);

		verify(notifications).paymentConfirmed(result);
		assertThat(result.amount()).isEqualByComparingTo(AMOUNT);
		assertThat(result.divided()).isFalse();
	}
}
