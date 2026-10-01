package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.admission.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase.ConfirmPaymentResult;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CheckoutAttemptRepository;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.InvalidPaymentVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConfirmFichaPaymentVerifiedUseCaseImpl}.
 *
 * <p>Two things here are the actual point of the class, and both used to be wrong.
 *
 * <p>The gate is <b>captured money</b>, not {@code SUCCESS}. The old code accepted any
 * {@code result=SUCCESS} with a matching amount, which is a label the gateway prints
 * rather than proof that money moved; {@code aSuccessWithNothingCapturedIsNotAPayment}
 * pins the difference and is the regression this interactor existed to prevent.
 *
 * <p>The {@code orderId} is checked against {@link CheckoutAttempt} rather than against
 * {@code payment.orderId}. That column is overwritten on every retry, so the real-world
 * case {@code anAttemptThatIsNoLongerTheLiveOrderStillConfirms} — the applicant paid,
 * the browser re-entered checkout before the confirmation landed, and the column now
 * holds the second order — was unanswerable before.
 */
@ExtendWith(MockitoExtension.class)
class ConfirmFichaPaymentVerifiedUseCaseImplTest {

	private static final UUID CANDIDATE_ID = UUID.randomUUID();

	private static final String ORDER_ID = "TESTUTEZ-ADM-2026-000001";

	private static final BigDecimal QUOTED = new BigDecimal("500.00");

	private final ConfirmPaymentResult paid = new ConfirmPaymentResult(CANDIDATE_ID, "ADM-2026-000001",
			CandidateStatus.PAID, "REF-2026-000001", QUOTED, Instant.parse("2026-09-24T12:00:00Z"),
			"REC-20260924-000001");

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private AdmissionPaymentRepository admissionPaymentRepository;

	@Mock
	private EvoPaymentsGatewayPort evoPaymentsGateway;

	@Mock
	private ConfirmAdmissionPaymentUseCase confirmAdmissionPaymentUseCase;

	@Mock
	private CheckoutAttemptRepository checkoutAttemptRepository;

	@Mock
	private CheckoutSlotClaimer checkoutSlotClaimer;

	private ConfirmFichaPaymentVerifiedUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ConfirmFichaPaymentVerifiedUseCaseImpl(candidateRepository, admissionPaymentRepository,
				evoPaymentsGateway, confirmAdmissionPaymentUseCase, checkoutAttemptRepository, checkoutSlotClaimer);
	}

	private static Candidate candidate() {
		return new Candidate(UUID.randomUUID(), UUID.randomUUID(), "ADM-2026-000001", true, true, null);
	}

	private static AdmissionPayment payment() {
		return new AdmissionPayment(CANDIDATE_ID, AdmissionPaymentConcept.ADMISSION_FICHA, QUOTED, "REF-2026-000001",
				LocalDate.now().plusDays(10));
	}

	/** The ficha whose live order is {@code ORDER_ID}, with that attempt open. */
	private AdmissionPayment pendingFichaWithThisAttempt() {
		AdmissionPayment payment = payment();
		payment.registerCheckout(ORDER_ID, "SESSION0001BR");
		givenOpenAttempt(ORDER_ID, CANDIDATE_ID, QUOTED);
		return payment;
	}

	private void givenOpenAttempt(String orderId, UUID candidateId, BigDecimal amount) {
		org.mockito.Mockito.lenient().when(checkoutAttemptRepository.findByOrderId(orderId)).thenReturn(Optional
				.of(new CheckoutAttempt(orderId, candidateId, amount, Instant.parse("2026-09-30T12:00:00Z"))));
	}

	/** The 3-arg shape marks the money as captured whenever the verdict is SUCCESS. */
	private EvoPaymentsGatewayPort.EvoOrderStatus capturedStatus() {
		return new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "SUCCESS", QUOTED);
	}

	private void givenCandidateAndPayment(AdmissionPayment payment) {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment));
	}

	@Test
	void onlineConfirmDelegatesLocalConfirmOnSuccess() {
		givenCandidateAndPayment(pendingFichaWithThisAttempt());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(capturedStatus());
		when(confirmAdmissionPaymentUseCase.confirm(CANDIDATE_ID)).thenReturn(paid);

		ConfirmPaymentResult result = useCase.confirm(CANDIDATE_ID, ORDER_ID);

		assertThat(result.receiptNumber()).isEqualTo("REC-20260924-000001");
		verify(evoPaymentsGateway).retrieveOrder(ORDER_ID);
		verify(confirmAdmissionPaymentUseCase).confirm(CANDIDATE_ID);
	}

	/**
	 * The invariant the nightly sweep rests on: a paid ficha has no open attempt, so
	 * tomorrow's run has nothing to ask about.
	 *
	 * <p>Nothing ever wrote {@link CheckoutAttemptCloseReason#CAPTURED} before this. Every
	 * paid ficha therefore left its attempt open for good, and the sweep re-queried the
	 * bank about it every night, forever — and asked to release a slot that a paid ficha
	 * holds permanently.
	 */
	@Test
	void aConfirmedPaymentClosesItsAttemptAsCaptured() {
		givenCandidateAndPayment(pendingFichaWithThisAttempt());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(capturedStatus());
		when(confirmAdmissionPaymentUseCase.confirm(CANDIDATE_ID)).thenReturn(paid);

		useCase.confirm(CANDIDATE_ID, ORDER_ID);

		verify(checkoutSlotClaimer).closeAttempt(ORDER_ID, CheckoutAttemptCloseReason.CAPTURED);
	}

	/**
	 * The regression this interactor existed to prevent. {@code SUCCESS} with an amount is
	 * what the gateway prints for an order it considers fine — it is a label, not proof
	 * that money moved, and accepting it is how a ficha gets marked paid for a payment
	 * that never happened.
	 */
	@Test
	void aSuccessWithNothingCapturedIsNotAPayment() {
		givenCandidateAndPayment(pendingFichaWithThisAttempt());
		// The 4-arg shape: SUCCESS and the amount, but totalCapturedAmount explicitly null.
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "SUCCESS", QUOTED, null));

		assertThatThrownBy(() -> useCase.confirm(CANDIDATE_ID, ORDER_ID))
				.isInstanceOf(InvalidPaymentVerificationException.class)
				.hasMessageContaining("no fue confirmado por el procesador");
		verify(confirmAdmissionPaymentUseCase, never()).confirm(any());
		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
	}

	@Test
	void rejectsFailureVerdictFromGateway() {
		givenCandidateAndPayment(pendingFichaWithThisAttempt());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "FAILURE", null));

		assertThatThrownBy(() -> useCase.confirm(CANDIDATE_ID, ORDER_ID))
				.isInstanceOf(InvalidPaymentVerificationException.class)
				.hasMessageContaining("no fue confirmado por el procesador");
		verify(confirmAdmissionPaymentUseCase, never()).confirm(any());
	}

	/**
	 * A capture that beats the verdict. §6 already decided capture outranks {@code result},
	 * and this is the other end of the same rule: money that arrived is not going back
	 * because the label says {@code FAILURE}.
	 */
	@Test
	void aCaptureOutranksTheVerdict() {
		givenCandidateAndPayment(pendingFichaWithThisAttempt());
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(
				ORDER_ID, "FAILURE", QUOTED, null, QUOTED, null, null, null, null, null));
		when(confirmAdmissionPaymentUseCase.confirm(CANDIDATE_ID)).thenReturn(paid);

		useCase.confirm(CANDIDATE_ID, ORDER_ID);

		verify(confirmAdmissionPaymentUseCase).confirm(CANDIDATE_ID);
	}

	/**
	 * The retry case that forced the orderId check to move off {@code payment.orderId}.
	 *
	 * <p>The applicant paid attempt 1, re-entered checkout before the confirmation landed,
	 * and the ficha's column now holds attempt 2. Settling attempt 1 is exactly what the
	 * sweep and the returning browser both need to do, and under the old equality check
	 * both were answered with "no corresponde a esta ficha".
	 */
	@Test
	void anAttemptThatIsNoLongerTheLiveOrderStillConfirms() {
		AdmissionPayment payment = payment();
		payment.registerCheckout("TESTUTEZ-ADM-2026-000001-SEGUNDO", "SESSION0002BR");
		givenCandidateAndPayment(payment);
		givenOpenAttempt(ORDER_ID, CANDIDATE_ID, QUOTED);
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(capturedStatus());
		when(confirmAdmissionPaymentUseCase.confirm(CANDIDATE_ID)).thenReturn(paid);

		ConfirmPaymentResult result = useCase.confirm(CANDIDATE_ID, ORDER_ID);

		assertThat(result.candidateStatus()).isEqualTo(CandidateStatus.PAID);
		verify(checkoutSlotClaimer).closeAttempt(ORDER_ID, CheckoutAttemptCloseReason.CAPTURED);
	}

	/**
	 * The security half of accepting any of her own attempts. An order that belongs to
	 * somebody else is not hers, whatever the body claims — otherwise this endpoint could
	 * be used to settle another applicant's ficha with her own order.
	 */
	@Test
	void anOrderBelongingToAnotherFichaIsRejected() {
		givenCandidateAndPayment(payment());
		givenOpenAttempt(ORDER_ID, CANDIDATE_ID, QUOTED);
		String someoneElsesOrder = "TESTUTEZ-ADM-2026-000099";
		when(checkoutAttemptRepository.findByOrderId(someoneElsesOrder)).thenReturn(Optional
				.of(new CheckoutAttempt(someoneElsesOrder, UUID.randomUUID(), QUOTED,
						Instant.parse("2026-09-30T12:00:00Z"))));

		assertThatThrownBy(() -> useCase.confirm(CANDIDATE_ID, someoneElsesOrder))
				.isInstanceOf(InvalidPaymentVerificationException.class)
				.hasMessageContaining("no corresponde a esta ficha");
		verify(evoPaymentsGateway, never()).retrieveOrder(any());
		verify(confirmAdmissionPaymentUseCase, never()).confirm(any());
	}

	@Test
	void anUnknownOrderIdIsRejectedBeforeTheGatewayIsAsked() {
		givenCandidateAndPayment(payment());
		givenOpenAttempt(ORDER_ID, CANDIDATE_ID, QUOTED);
		when(checkoutAttemptRepository.findByOrderId("TESTUTEZ-ADM-2026-000404")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.confirm(CANDIDATE_ID, "TESTUTEZ-ADM-2026-000404"))
				.isInstanceOf(InvalidPaymentVerificationException.class)
				.hasMessageContaining("no corresponde a esta ficha");
		verify(evoPaymentsGateway, never()).retrieveOrder(any());
	}

	/**
	 * A settled attempt cannot be settled again. {@code close()} refuses to re-close, so
	 * accepting it here would let a replayed confirm re-run the local transition against
	 * a row the bank has already answered for.
	 */
	@Test
	void anAlreadyClosedAttemptIsRejected() {
		givenCandidateAndPayment(pendingFichaWithThisAttempt());
		CheckoutAttempt settled = new CheckoutAttempt(ORDER_ID, CANDIDATE_ID, QUOTED,
				Instant.parse("2026-09-30T12:00:00Z"));
		settled.close(CheckoutAttemptCloseReason.REJECTED, Instant.parse("2026-09-30T12:05:00Z"));
		when(checkoutAttemptRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(settled));

		assertThatThrownBy(() -> useCase.confirm(CANDIDATE_ID, ORDER_ID))
				.isInstanceOf(InvalidPaymentVerificationException.class)
				.hasMessageContaining("no corresponde a esta ficha");
		verify(evoPaymentsGateway, never()).retrieveOrder(any());
	}

	@Test
	void onlineConfirmRejectsWhenClientOmitsOrderId() {
		// orderId is MANDATORY: without it there is nothing to cross-check the
		// return against, and the previous fallback marked the ficha PAID on a
		// bare POST. Now it must be rejected before EVO is ever consulted.
		givenCandidateAndPayment(payment());
		givenOpenAttempt(ORDER_ID, CANDIDATE_ID, QUOTED);

		assertThatThrownBy(() -> useCase.confirm(CANDIDATE_ID, null))
				.isInstanceOf(InvalidPaymentVerificationException.class)
				.hasMessageContaining("identificador del pedido es obligatorio");
		verify(evoPaymentsGateway, never()).retrieveOrder(any());
		verify(confirmAdmissionPaymentUseCase, never()).confirm(any());
	}

	/**
	 * A capture below the price this attempt quoted still marks the ficha paid.
	 *
	 * <p>Not a judgement call about fairness — a consequence of EVO orders being
	 * immutable. The cash is at the bank and cannot be recalled, so refusing would leave a
	 * ficha {@code PENDING} with money captured, whose owner presses "Pagar" again and
	 * pays twice. The tariff is re-quoted per checkout, so a gap is only reachable when
	 * the catalog moved between two attempts: an operational fact for the office, logged
	 * in {@code warnIfUnderpaid}, never a reason to withhold a paid admission.
	 */
	@Test
	void aCaptureBelowTheQuotedTariffStillPaysTheFicha() {
		BigDecimal oldTariff = new BigDecimal("450.00");
		AdmissionPayment payment = payment();
		payment.registerCheckout(ORDER_ID, "SESSION0001BR");
		givenCandidateAndPayment(payment);
		givenOpenAttempt(ORDER_ID, CANDIDATE_ID, oldTariff);
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(new EvoPaymentsGatewayPort.EvoOrderStatus(
				ORDER_ID, "SUCCESS", oldTariff, null, oldTariff, null, null, null, null, null));
		when(confirmAdmissionPaymentUseCase.confirm(CANDIDATE_ID)).thenReturn(paid);

		ConfirmPaymentResult result = useCase.confirm(CANDIDATE_ID, ORDER_ID);

		assertThat(result.candidateStatus()).isEqualTo(CandidateStatus.PAID);
		verify(confirmAdmissionPaymentUseCase).confirm(CANDIDATE_ID);
	}

	@Test
	void legacyWindowConfirmIsRejectedWithNoOrderId() {
		// The window-payment path was removed: a pending ficha with no online
		// session and no orderId can no longer be confirmed. Previously this
		// delegated straight to the local confirm and returned PAID.
		givenCandidateAndPayment(payment());

		assertThatThrownBy(() -> useCase.confirm(CANDIDATE_ID, null))
				.isInstanceOf(InvalidPaymentVerificationException.class)
				.hasMessageContaining("identificador del pedido es obligatorio");
		verify(evoPaymentsGateway, never()).retrieveOrder(any());
		verify(confirmAdmissionPaymentUseCase, never()).confirm(any());
	}

	@Test
	void alreadyPaidIs409WithoutHittingTheGateway() {
		AdmissionPayment paidPayment = payment();
		paidPayment.markPaid("REC-20260924-000001");
		givenCandidateAndPayment(paidPayment);

		assertThatThrownBy(() -> useCase.confirm(CANDIDATE_ID, ORDER_ID))
				.isInstanceOf(CandidateAlreadyPaidException.class)
				.hasMessageContaining("ya estaba pagada");
		verify(evoPaymentsGateway, never()).retrieveOrder(any());
		verify(confirmAdmissionPaymentUseCase, never()).confirm(any());
	}

	@Test
	void missingCandidateIs404() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.confirm(CANDIDATE_ID, ORDER_ID))
				.isInstanceOf(CandidateNotFoundException.class);
	}

	@Test
	void missingPaymentIs404() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.confirm(CANDIDATE_ID, ORDER_ID))
				.isInstanceOf(CandidateNotFoundException.class);
	}
}