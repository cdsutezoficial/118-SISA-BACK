package mx.edu.utez.sisa.admission.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.admission.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionQuotaPort;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CheckoutAttemptRepository;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigCapacityReachedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link CheckoutSlotClaimer}, the rule that makes
 * "no more than {@code maxCandidates} fichas" true.
 *
 * <p>Three things are being pinned here, and each one is a way the design could
 * have quietly failed:
 * <ol>
 * <li><b>The lock is taken before the count is read</b>, and the count asks about
 * the admission config's occupancy rather than the whole program's payments.
 * Getting the order wrong reintroduces the race the lock exists to close; keying
 * the count by program let a full old cycle close a new one.</li>
 * <li><b>The candidate's own claim is excluded from the count.</b> Without it, a
 * candidate retrying a checkout is refused for a slot they already hold, which
 * only shows up on the last place of a full career — the worst possible moment to
 * discover it.</li>
 * <li><b>Refusal writes nothing.</b> A rejected claim must leave the ficha
 * exactly as it was, or a career would silently shrink every time somebody was
 * turned away.</li>
 * </ol>
 *
 * <p>What this class cannot prove is the mutual exclusion itself: {@code
 * AdmissionQuotaPort#lockQuota} is mocked, so the row lock is asserted as an
 * intent, not as an observed serialisation. That is
 * {@code CheckoutSlotClaimerConcurrencyIT}'s job, and pretending otherwise here
 * would be a test that passes for the wrong reason.
 */
@ExtendWith(MockitoExtension.class)
class CheckoutSlotClaimerTest {

	private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

	private static final Instant NOW = TODAY.atTime(12, 0).atZone(ZONE).toInstant();

	private static final int MAX_CANDIDATES = 15;

	/**
	 * The ficha's own payment window, the default the application ships with. It
	 * reaches the claim because a claim stops holding a slot once its ficha's window
	 * is over, so the count it compares against has to know the same number.
	 */
	private static final int PAYMENT_WINDOW_DAYS = 10;

	/**
	 * The live tariff the checkout resolved for this claim, distinct from the
	 * 500.00 quote {@link #payment()} carries, so the reprice is observable.
	 */
	private static final BigDecimal CHECKOUT_AMOUNT = new BigDecimal("550.00");

	private static final UUID CANDIDATE_ID = UUID.randomUUID();

	private static final UUID ADMISSION_CONFIG_ID = UUID.randomUUID();

	@Mock
	private AdmissionQuotaPort admissionQuotaPort;

	@Mock
	private AdmissionPaymentRepository admissionPaymentRepository;

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private CheckoutAttemptRepository checkoutAttemptRepository;

	private CheckoutSlotClaimer claimer;

	@BeforeEach
	void setUp() {
		claimer = new CheckoutSlotClaimer(admissionQuotaPort, admissionPaymentRepository, candidateRepository,
				checkoutAttemptRepository, Clock.fixed(NOW, ZONE), PAYMENT_WINDOW_DAYS);
		lenientCandidateAndPayment();
	}

	private void lenientCandidateAndPayment() {
		lenient().when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		lenient().when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID))
				.thenReturn(Optional.of(payment()));
		lenient().when(admissionQuotaPort.lockQuota(ADMISSION_CONFIG_ID))
				.thenReturn(new AdmissionQuotaPort.QuotaState(MAX_CANDIDATES));
		lenient().when(admissionPaymentRepository.countOccupiedByConfigIdExcludingCandidate(any(), any(), any(),
				anyInt())).thenReturn(0L);
	}

	private static Candidate candidate() {
		return new Candidate(UUID.randomUUID(), ADMISSION_CONFIG_ID, "ADM-2026-000001", true, true, null);
	}

	private static AdmissionPayment payment() {
		return new AdmissionPayment(CANDIDATE_ID, AdmissionPaymentConcept.ADMISSION_FICHA, new BigDecimal("500.00"),
				"REF-2026-000001", TODAY.plusDays(10));
	}

	@Test
	void aFreeSlotIsClaimedAndStamped() {
		claimer.claim(CANDIDATE_ID, CHECKOUT_AMOUNT);

		ArgumentCaptor<AdmissionPayment> saved = ArgumentCaptor.forClass(AdmissionPayment.class);
		verify(admissionPaymentRepository).save(saved.capture());
		assertThat(saved.getValue().getCheckoutClaimedAt()).isNotNull();
		// The claim also stamps the live tariff, so the amount the confirmation
		// later checks against the bank is the one quoted at the click, not the
		// registration quote.
		assertThat(saved.getValue().getAmount()).isEqualByComparingTo(CHECKOUT_AMOUNT);
	}

	/**
	 * The lock has to be held before the count is read, or two candidates can both
	 * read the same number and both conclude there is room.
	 */
	@Test
	void theQuotaIsLockedBeforeItIsCounted() {
		claimer.claim(CANDIDATE_ID, CHECKOUT_AMOUNT);

		InOrder inOrder = inOrder(admissionQuotaPort, admissionPaymentRepository);
		inOrder.verify(admissionQuotaPort).lockQuota(ADMISSION_CONFIG_ID);
		inOrder.verify(admissionPaymentRepository)
				.countOccupiedByConfigIdExcludingCandidate(ADMISSION_CONFIG_ID, CANDIDATE_ID, TODAY, PAYMENT_WINDOW_DAYS);
	}

	/**
	 * The count is scoped to the candidate's own admission config and to today,
	 * because the claim expires against the tuition concept's window and a program
	 * sold in two periods has two independent quotas.
	 */
	@Test
	void theCountIsScopedToTheConfigAndToToday() {
		claimer.claim(CANDIDATE_ID, CHECKOUT_AMOUNT);

		verify(admissionPaymentRepository).countOccupiedByConfigIdExcludingCandidate(ADMISSION_CONFIG_ID, CANDIDATE_ID,
				TODAY, PAYMENT_WINDOW_DAYS);
		verify(admissionPaymentRepository, never()).countOccupiedByConfigId(any(), any(), anyInt());
	}

	/** Off by one, the classic: fifteen sold means the sixteenth is refused. */
	@Test
	void aFullQuotaIsRefused() {
		when(admissionPaymentRepository.countOccupiedByConfigIdExcludingCandidate(ADMISSION_CONFIG_ID, CANDIDATE_ID, TODAY, PAYMENT_WINDOW_DAYS))
				.thenReturn((long) MAX_CANDIDATES);

		assertThatThrownBy(() -> claimer.claim(CANDIDATE_ID, CHECKOUT_AMOUNT))
				.isInstanceOf(ProgramAdmissionConfigCapacityReachedException.class);
	}

	/**
	 * The sentence a candidate reads when a career is full, matched in full rather
	 * than by substring.
	 *
	 * <p>This is the whole sentence on purpose, and it names no number. It shipped as
	 * "La carrera alcanzo su cupo de 1 fichas pagadas", which was wrong three ways: a
	 * grammar slip, a claim that the quota was taken by people who paid, and a figure
	 * that invites "when does a place free up?" — a question with no answer, because
	 * the quota is a cap and there is no queue. The count includes checkouts still in
	 * flight, so the "paid" wording also told candidates they had lost to paying
	 * applicants when the last place might have been sitting unsettled.
	 *
	 * <p>Matched in full so that reintroducing the count breaks the build: the number
	 * is a deliberate omission, not an oversight, and the test is where that is
	 * recorded.
	 */
	@Test
	void theFullQuotaMessageIsPinned() {
		when(admissionQuotaPort.lockQuota(ADMISSION_CONFIG_ID))
				.thenReturn(new AdmissionQuotaPort.QuotaState(MAX_CANDIDATES));
		when(admissionPaymentRepository.countOccupiedByConfigIdExcludingCandidate(ADMISSION_CONFIG_ID, CANDIDATE_ID, TODAY, PAYMENT_WINDOW_DAYS))
				.thenReturn((long) MAX_CANDIDATES);

		assertThatThrownBy(() -> claimer.claim(CANDIDATE_ID, CHECKOUT_AMOUNT))
				.hasMessage("El cupo de esta carrera se agotó.");
	}

/** One place left must still be claimable, or the last place could never sell. */
	@Test
	void theLastFreeSlotIsClaimable() {
		when(admissionPaymentRepository.countOccupiedByConfigIdExcludingCandidate(ADMISSION_CONFIG_ID, CANDIDATE_ID, TODAY, PAYMENT_WINDOW_DAYS))
				.thenReturn((long) MAX_CANDIDATES - 1);

		claimer.claim(CANDIDATE_ID, CHECKOUT_AMOUNT);

		verify(admissionPaymentRepository).save(any());
	}

	/**
	 * A refusal must not touch the ficha. Saving a claim on the way out would
	 * shrink the career by one every time somebody was turned away, which shows up
	 * days later as a career that quietly sells fewer fichas than it advertises.
	 */
	@Test
	void aRefusedClaimWritesNothing() {
		when(admissionPaymentRepository.countOccupiedByConfigIdExcludingCandidate(ADMISSION_CONFIG_ID, CANDIDATE_ID, TODAY, PAYMENT_WINDOW_DAYS))
				.thenReturn((long) MAX_CANDIDATES);

		assertThatThrownBy(() -> claimer.claim(CANDIDATE_ID, CHECKOUT_AMOUNT))
				.isInstanceOf(ProgramAdmissionConfigCapacityReachedException.class);

		verify(admissionPaymentRepository, never()).save(any());
	}

	/**
	 * The retry case. A candidate whose checkout was abandoned already holds a
	 * claim; if the count included it, a career that had just sold its last place
	 * would refuse the retry for a slot that is theirs.
	 */
	@Test
	void aRetryIsCountedAsRoomBesideItsOwnClaimNotAgainstIt() {
		when(admissionPaymentRepository.countOccupiedByConfigIdExcludingCandidate(ADMISSION_CONFIG_ID, CANDIDATE_ID, TODAY, PAYMENT_WINDOW_DAYS))
				.thenReturn((long) MAX_CANDIDATES - 1);

		claimer.claim(CANDIDATE_ID, CHECKOUT_AMOUNT);

		verify(admissionPaymentRepository).save(any());
	}

	@Test
	void releasingHandsTheSlotBack() {
		AdmissionPayment claimed = payment();
		claimed.claimCheckoutSlot();
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(claimed));

		claimer.release(CANDIDATE_ID);

		ArgumentCaptor<AdmissionPayment> saved = ArgumentCaptor.forClass(AdmissionPayment.class);
		verify(admissionPaymentRepository).save(saved.capture());
		assertThat(saved.getValue().getCheckoutClaimedAt()).isNull();
	}

	/** Releasing must not re-read the quota: the slot is the candidate's to give back. */
	@Test
	void releasingDoesNotConsultTheQuota() {
		claimer.release(CANDIDATE_ID);

		verify(admissionQuotaPort, never()).lockQuota(any());
		verify(admissionPaymentRepository, never())
				.countOccupiedByConfigIdExcludingCandidate(any(), any(), any(), anyInt());
	}

	@Test
	void persistingTheSessionStoresBothGatewayIds() {
		claimer.persistCheckoutSession(CANDIDATE_ID, "TESTUTEZ-ADM-2026-000001-abc", "SESSION0001BR");

		ArgumentCaptor<AdmissionPayment> saved = ArgumentCaptor.forClass(AdmissionPayment.class);
		verify(admissionPaymentRepository).save(saved.capture());
		assertThat(saved.getValue().getOrderId()).isEqualTo("TESTUTEZ-ADM-2026-000001-abc");
		assertThat(saved.getValue().getCheckoutSessionId()).isEqualTo("SESSION0001BR");
	}

	// -- the attempt row (§3.6): opened before the gateway, closed once --

	/**
	 * The attempt carries everything the sweep will need to ask the bank about this
	 * order later: the id, whose ficha it is, the amount that was put in front of
	 * the applicant and when it was opened. The amount is snapshotted rather than
	 * re-read later so the capture is compared against the price that was actually
	 * charged, not against a tariff that may have been edited since.
	 */
	@Test
	void openingAnAttemptRecordsTheOrderTheCandidateTheAmountAndTheMoment() {
		claimer.openAttempt("TESTUTEZ-ADM-2026-000001-abc", CANDIDATE_ID, CHECKOUT_AMOUNT);

		ArgumentCaptor<CheckoutAttempt> saved = ArgumentCaptor.forClass(CheckoutAttempt.class);
		verify(checkoutAttemptRepository).save(saved.capture());
		CheckoutAttempt attempt = saved.getValue();
		assertThat(attempt.getOrderId()).isEqualTo("TESTUTEZ-ADM-2026-000001-abc");
		assertThat(attempt.getCandidateId()).isEqualTo(CANDIDATE_ID);
		assertThat(attempt.getAmount()).isEqualByComparingTo(CHECKOUT_AMOUNT);
		assertThat(attempt.getCreatedAt()).isEqualTo(NOW);
		assertThat(attempt.getCloseReason()).isEqualTo(CheckoutAttemptCloseReason.STARTED);
		assertThat(attempt.getClosedAt()).isNull();
		assertThat(attempt.isOpen()).isTrue();
	}

	/** Opening an attempt must not take a quota slot: that is {@link #claim}'s job. */
	@Test
	void openingAnAttemptDoesNotTouchTheFichaOrTheQuota() {
		claimer.openAttempt("TESTUTEZ-ADM-2026-000001-abc", CANDIDATE_ID, CHECKOUT_AMOUNT);

		verify(admissionPaymentRepository, never()).save(any());
		verify(admissionQuotaPort, never()).lockQuota(any());
	}

	@Test
	void closingAnAttemptStampsTheReasonAndTheMoment() {
		CheckoutAttempt open = new CheckoutAttempt("TESTUTEZ-ADM-2026-000001-abc", CANDIDATE_ID, CHECKOUT_AMOUNT, NOW);
		when(checkoutAttemptRepository.findByOrderId("TESTUTEZ-ADM-2026-000001-abc")).thenReturn(Optional.of(open));

		claimer.closeAttempt("TESTUTEZ-ADM-2026-000001-abc", CheckoutAttemptCloseReason.SESSION_TIMEOUT);

		ArgumentCaptor<CheckoutAttempt> saved = ArgumentCaptor.forClass(CheckoutAttempt.class);
		verify(checkoutAttemptRepository).save(saved.capture());
		assertThat(saved.getValue().getCloseReason()).isEqualTo(CheckoutAttemptCloseReason.SESSION_TIMEOUT);
		assertThat(saved.getValue().getClosedAt()).isEqualTo(NOW);
		assertThat(saved.getValue().isOpen()).isFalse();
	}

	/**
	 * A close with nothing to close is not an error. The browser can report a
	 * timeout for an attempt that was never recorded, and there is nothing to
	 * reconcile and nothing held — failing here would turn a harmless race into a
	 * 500 on the applicant's screen.
	 */
	@Test
	void closingAnUnknownAttemptIsANoOp() {
		when(checkoutAttemptRepository.findByOrderId("TESTUTEZ-ADM-2026-000001-abc")).thenReturn(Optional.empty());

		claimer.closeAttempt("TESTUTEZ-ADM-2026-000001-abc", CheckoutAttemptCloseReason.SESSION_TIMEOUT);

		verify(checkoutAttemptRepository, never()).save(any());
	}

	/**
	 * A candidate with no ficha cannot be released either, and saying so beats
	 * quietly doing nothing: a silent skip would leave the caller's retry loop
	 * holding a claim it could never give back.
	 */
	@Test
	void aMissingFichaIsReportedRatherThanSkipped() {
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> claimer.release(CANDIDATE_ID))
				.isInstanceOf(CandidateNotFoundException.class).hasMessageContaining("no tiene ficha de pago");
	}

	/**
	 * A paid ficha has no slot to give back — it already holds one permanently.
	 * Refusing to rewrite it keeps a late release from resurrecting a claim on a
	 * payment that is already settled.
	 */
	@Test
	void aPaidFichaCannotBeReclaimed() {
		AdmissionPayment paid = payment();
		paid.markPaid("REC-20260925-000001");
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(paid));

		assertThatThrownBy(() -> claimer.release(CANDIDATE_ID)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("PENDING");
	}
}
