package mx.edu.utez.sisa.admission.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionQuotaPort;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigCapacityReachedException;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.finance.domain.port.out.CheckoutAttemptRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link CheckoutSlotClaimer}.
 *
 * <p>Since 2026-10-23 a place is held by the open checkout attempt itself: claiming
 * <em>is</em> opening the attempt (under the config lock, after the quota count), and
 * closing the attempt is what gives the place back. An attempt older than 15 minutes
 * no longer counts, which is the query's job ({@code FichaOccupancyJpaRepository}).
 */
@ExtendWith(MockitoExtension.class)
class CheckoutSlotClaimerTest {

	private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

	private static final Instant NOW = TODAY.atTime(12, 0).atZone(ZONE).toInstant();

	private static final int MAX_CANDIDATES = 15;

	private static final BigDecimal CHECKOUT_AMOUNT = new BigDecimal("550.00");

	private static final UUID CANDIDATE_ID = UUID.randomUUID();

	private static final UUID CHARGE_ID = UUID.randomUUID();

	private static final String ORDER_ID = "TESTUTEZ-ADM-2026-000001-abc";

	private static final FichaPaymentPort.FichaQuote QUOTE = new FichaPaymentPort.FichaQuote(CHARGE_ID,
			"FICHA-ING", "Ficha de admisión", UUID.randomUUID(), new BigDecimal("600.00"), new BigDecimal("50.00"),
			CHECKOUT_AMOUNT, UUID.randomUUID(), false, false, null);

	private static final UUID ADMISSION_CONFIG_ID = UUID.randomUUID();

	/** Open attempts younger than this hold a place. */
	private static final int HOLD_MINUTES = 15;

	private static final Instant HOLD_SINCE = FichaPaymentWindow.attemptsHoldingSince(NOW, HOLD_MINUTES);

	@Mock
	private AdmissionQuotaPort admissionQuotaPort;

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private CheckoutAttemptRepository checkoutAttemptRepository;

	@Mock
	private FichaPaymentPort fichaPaymentPort;

	private CheckoutSlotClaimer claimer;

	private Candidate candidate;

	@BeforeEach
	void setUp() {
		claimer = new CheckoutSlotClaimer(admissionQuotaPort, candidateRepository, checkoutAttemptRepository,
				fichaPaymentPort, Clock.fixed(NOW, ZONE), HOLD_MINUTES);
		candidate = new Candidate(UUID.randomUUID(), ADMISSION_CONFIG_ID, "ADM-2026-000001", true, true, null);
		candidate.linkFichaPayment(CHARGE_ID);
		lenient().when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
		lenient().when(fichaPaymentPort.findCharge(CHARGE_ID))
				.thenReturn(Optional.of(FichaTestData.pendingCharge(CHARGE_ID)));
		lenient().when(admissionQuotaPort.lockQuota(ADMISSION_CONFIG_ID))
				.thenReturn(new AdmissionQuotaPort.QuotaState(MAX_CANDIDATES));
		lenient().when(admissionQuotaPort.countOccupiedExcluding(any(), any(), any())).thenReturn(0L);
	}

	private void occupiedByOthers(long count) {
		when(admissionQuotaPort.countOccupiedExcluding(ADMISSION_CONFIG_ID, CANDIDATE_ID, HOLD_SINCE))
				.thenReturn(count);
	}

	// -- claiming = opening the attempt --

	@Test
	void aFreePlaceIsClaimedByOpeningTheAttemptWithItsSnapshot() {
		claimer.claim(CANDIDATE_ID, ORDER_ID, QUOTE);

		ArgumentCaptor<CheckoutAttempt> saved = ArgumentCaptor.forClass(CheckoutAttempt.class);
		verify(checkoutAttemptRepository).save(saved.capture());
		CheckoutAttempt attempt = saved.getValue();
		assertThat(attempt.getOrderId()).isEqualTo(ORDER_ID);
		assertThat(attempt.getPaymentId()).isEqualTo(CHARGE_ID);
		assertThat(attempt.getCandidateId()).isEqualTo(CANDIDATE_ID);
		assertThat(attempt.getStudentId()).isNull();
		assertThat(attempt.getAmount()).isEqualByComparingTo(CHECKOUT_AMOUNT);
		// The whole snapshot travels with the attempt, so whichever attempt the
		// bank captures is booked with the numbers it charged.
		assertThat(attempt.getOriginalAmount()).isEqualByComparingTo("600.00");
		assertThat(attempt.getDiscountAmount()).isEqualByComparingTo("50.00");
		assertThat(attempt.getBenefitId()).isEqualTo(QUOTE.benefitId());
		assertThat(attempt.getRateId()).isEqualTo(QUOTE.rateId());
		assertThat(attempt.hasSnapshot()).isTrue();
		assertThat(attempt.getCreatedAt()).isEqualTo(NOW);
		assertThat(attempt.getCloseReason()).isEqualTo(CheckoutAttemptCloseReason.STARTED);
		assertThat(attempt.isOpen()).isTrue();
	}

	@Test
	void theQuotaIsLockedBeforeItIsCountedAndCountedBeforeTheAttemptIsWritten() {
		claimer.claim(CANDIDATE_ID, ORDER_ID, QUOTE);

		InOrder inOrder = inOrder(admissionQuotaPort, checkoutAttemptRepository);
		inOrder.verify(admissionQuotaPort).lockQuota(ADMISSION_CONFIG_ID);
		inOrder.verify(admissionQuotaPort).countOccupiedExcluding(ADMISSION_CONFIG_ID, CANDIDATE_ID, HOLD_SINCE);
		inOrder.verify(checkoutAttemptRepository).save(any());
	}

	/** Only attempts younger than the 15-minute hold count, measured from the claim's own clock. */
	@Test
	void theCountIsScopedToTheConfigExcludesTheCandidateAndUsesTheFifteenMinuteHold() {
		claimer.claim(CANDIDATE_ID, ORDER_ID, QUOTE);

		verify(admissionQuotaPort).countOccupiedExcluding(eq(ADMISSION_CONFIG_ID), eq(CANDIDATE_ID),
				eq(NOW.minusSeconds(15 * 60)));
	}

	@Test
	void aFullQuotaIsRefusedWithThePinnedMessageAndLeavesNoAttempt() {
		occupiedByOthers(MAX_CANDIDATES);

		assertThatThrownBy(() -> claimer.claim(CANDIDATE_ID, ORDER_ID, QUOTE))
				.isInstanceOf(ProgramAdmissionConfigCapacityReachedException.class)
				.hasMessage("El cupo de esta carrera se agotó.");

		verify(checkoutAttemptRepository, never()).save(any());
	}

	@Test
	void theLastFreePlaceIsClaimable() {
		occupiedByOthers(MAX_CANDIDATES - 1);

		claimer.claim(CANDIDATE_ID, ORDER_ID, QUOTE);

		verify(checkoutAttemptRepository).save(any());
	}

	@Test
	void aPaidFichaCannotOpenAnotherAttempt() {
		when(fichaPaymentPort.findCharge(CHARGE_ID)).thenReturn(Optional.of(FichaTestData.paidCharge(CHARGE_ID)));

		assertThatThrownBy(() -> claimer.claim(CANDIDATE_ID, ORDER_ID, QUOTE))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("no está pendiente");
		verify(checkoutAttemptRepository, never()).save(any());
	}

	@Test
	void aMissingFichaIsReportedRatherThanSkipped() {
		when(fichaPaymentPort.findCharge(CHARGE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> claimer.claim(CANDIDATE_ID, ORDER_ID, QUOTE))
				.isInstanceOf(CandidateNotFoundException.class).hasMessageContaining("no tiene ficha de pago");
	}

	@Test
	void anUnknownCandidateIsReported() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> claimer.claim(CANDIDATE_ID, ORDER_ID, QUOTE))
				.isInstanceOf(CandidateNotFoundException.class);
	}

	// -- the exemption --

	@Test
	void anExemptQuoteSettlesTheChargeAndMarksTheCandidatePaid() {
		FichaPaymentPort.FichaQuote exempt = new FichaPaymentPort.FichaQuote(CHARGE_ID, "FICHA-ING", "Ficha",
				UUID.randomUUID(), new BigDecimal("600.00"), new BigDecimal("600.00"), BigDecimal.ZERO.setScale(2),
				UUID.randomUUID(), true, false, null);

		String receipt = claimer.settleExempt(CANDIDATE_ID, exempt);

		assertThat(receipt).startsWith("EXE-20260925-");
		ArgumentCaptor<FichaPaymentPort.SettleFichaCommand> settled = ArgumentCaptor
				.forClass(FichaPaymentPort.SettleFichaCommand.class);
		verify(fichaPaymentPort).settle(settled.capture());
		assertThat(settled.getValue().financePaymentId()).isEqualTo(CHARGE_ID);
		assertThat(settled.getValue().receiptNumber()).isEqualTo(receipt);
		assertThat(settled.getValue().reference()).isEqualTo(FichaTestData.REFERENCE);
		assertThat(settled.getValue().charged().amountDue()).isEqualByComparingTo("0.00");
		assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.PAID);
		verify(candidateRepository).save(candidate);
	}

	// -- closing = releasing --

	@Test
	void closingAnAttemptStampsTheReasonAndTheMoment() {
		CheckoutAttempt open = FichaTestData.attempt(ORDER_ID, CHARGE_ID, CANDIDATE_ID, CHECKOUT_AMOUNT, NOW);
		when(checkoutAttemptRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(open));

		claimer.closeAttempt(ORDER_ID, CheckoutAttemptCloseReason.SESSION_TIMEOUT);

		ArgumentCaptor<CheckoutAttempt> saved = ArgumentCaptor.forClass(CheckoutAttempt.class);
		verify(checkoutAttemptRepository).save(saved.capture());
		assertThat(saved.getValue().getCloseReason()).isEqualTo(CheckoutAttemptCloseReason.SESSION_TIMEOUT);
		assertThat(saved.getValue().getClosedAt()).isEqualTo(NOW);
		assertThat(saved.getValue().isOpen()).isFalse();
	}

	@Test
	void closingDoesNotConsultTheQuota() {
		CheckoutAttempt open = FichaTestData.attempt(ORDER_ID, CHARGE_ID, CANDIDATE_ID, CHECKOUT_AMOUNT, NOW);
		when(checkoutAttemptRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(open));

		claimer.closeAttempt(ORDER_ID, CheckoutAttemptCloseReason.REJECTED);

		verify(admissionQuotaPort, never()).lockQuota(any());
		verify(admissionQuotaPort, never()).countOccupiedExcluding(any(), any(), any());
	}

	/** A closed attempt is history: closing it again writes nothing. */
	@Test
	void anAlreadyClosedAttemptIsNotRewritten() {
		CheckoutAttempt closed = FichaTestData.attempt(ORDER_ID, CHARGE_ID, CANDIDATE_ID, CHECKOUT_AMOUNT, NOW);
		closed.close(CheckoutAttemptCloseReason.CAPTURED, NOW);
		when(checkoutAttemptRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(closed));

		claimer.closeAttempt(ORDER_ID, CheckoutAttemptCloseReason.REJECTED);

		verify(checkoutAttemptRepository, never()).save(any());
		assertThat(closed.getCloseReason()).isEqualTo(CheckoutAttemptCloseReason.CAPTURED);
	}

	@Test
	void closingAnUnknownAttemptIsANoOp() {
		when(checkoutAttemptRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

		claimer.closeAttempt(ORDER_ID, CheckoutAttemptCloseReason.SESSION_TIMEOUT);

		verify(checkoutAttemptRepository, never()).save(any());
	}

	@Test
	void persistingTheSessionStoresItOnTheAttempt() {
		CheckoutAttempt open = FichaTestData.attempt(ORDER_ID, CHARGE_ID, CANDIDATE_ID, CHECKOUT_AMOUNT, NOW);
		when(checkoutAttemptRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(open));

		claimer.persistCheckoutSession(ORDER_ID, "SESSION0001BR");

		ArgumentCaptor<CheckoutAttempt> saved = ArgumentCaptor.forClass(CheckoutAttempt.class);
		verify(checkoutAttemptRepository).save(saved.capture());
		assertThat(saved.getValue().getSessionId()).isEqualTo("SESSION0001BR");
	}
}
