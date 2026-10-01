package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.admission.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase.ConfirmPaymentResult;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmFichaPaymentVerifiedUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CheckoutAttemptRepository;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.InvalidPaymentVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * EVO-verified payment-confirmation interactor (Fase 5 of the payment plan):
 * the entry point of {@code POST /candidates/{id}/payments/confirm} when the
 * ficha went through the online checkout. The gateway's {@code order.id} was
 * persisted by {@code InitiateFichaPaymentUseCaseImpl}, and the applicant's
 * return carries it back; before ANY local {@code markPaid}, this use case
 * asks EVO {@code RETRIEVE_ORDER} and demands {@code result=SUCCESS} AND the
 * exact {@code payment.amount} — a failed/pending/unknown verdict or a
 * mismatched amount is an {@link InvalidPaymentVerificationException} (→ 400),
 * never a false "pagado".
 *
 * <p>Validations, in order:
 * <ol>
 * <li>candidate + payment must exist (404);</li>
 * <li>already-paid ficha → 409 {@code CandidateAlreadyPaidException} without
 * even calling EVO (repeat after success stays idempotent);</li>
 * <li>the request MUST carry an {@code orderId} — the window-payment path was
 * removed, so an absent one is a 400, never a silent local "pagado";</li>
 * <li>the ficha never initiated online (no open attempt at all) → 400 (no
 * session exists to verify);</li>
 * <li>the {@code orderId} must be an <b>open attempt of this very ficha</b> → 400
 * otherwise;</li>
 * <li>{@code RETRIEVE_ORDER} must show money actually captured →
 * 400 otherwise;</li>
 * <li>only then the transition happens through the existing
 * {@link ConfirmAdmissionPaymentUseCase} (receipt, candidate {@code PAID},
 * idempotent 409), and the attempt is closed as {@code CAPTURED}.</li>
 * </ol>
 *
 * <p>SECURITY: {@code orderId} is mandatory on purpose. The previous
 * implementation accepted an absent one and fell through to
 * {@link ConfirmAdmissionPaymentUseCase#confirm(UUID)}, marking ANY pending
 * ficha {@code PAID} without asking the gateway — anyone who could guess a
 * candidate UUID could mark registrations paid. With the window path gone
 * there is exactly one way in, and it always crosses EVO.
 */
public class ConfirmFichaPaymentVerifiedUseCaseImpl implements ConfirmFichaPaymentVerifiedUseCase {

	private static final Logger log = LoggerFactory.getLogger(ConfirmFichaPaymentVerifiedUseCaseImpl.class);

	private final CandidateRepository candidateRepository;

	private final AdmissionPaymentRepository admissionPaymentRepository;

	private final EvoPaymentsGatewayPort evoPaymentsGateway;

	private final ConfirmAdmissionPaymentUseCase confirmAdmissionPaymentUseCase;

	private final CheckoutAttemptRepository checkoutAttemptRepository;

	private final CheckoutSlotClaimer checkoutSlotClaimer;

	public ConfirmFichaPaymentVerifiedUseCaseImpl(CandidateRepository candidateRepository,
			AdmissionPaymentRepository admissionPaymentRepository, EvoPaymentsGatewayPort evoPaymentsGateway,
			ConfirmAdmissionPaymentUseCase confirmAdmissionPaymentUseCase,
			CheckoutAttemptRepository checkoutAttemptRepository, CheckoutSlotClaimer checkoutSlotClaimer) {
		this.candidateRepository = candidateRepository;
		this.admissionPaymentRepository = admissionPaymentRepository;
		this.evoPaymentsGateway = evoPaymentsGateway;
		this.confirmAdmissionPaymentUseCase = confirmAdmissionPaymentUseCase;
		this.checkoutAttemptRepository = checkoutAttemptRepository;
		this.checkoutSlotClaimer = checkoutSlotClaimer;
	}

	@Override
	@Transactional
	public ConfirmPaymentResult confirm(UUID candidateId, String orderId) {
		Candidate candidate = candidateRepository.findById(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException("No existe el candidato: " + candidateId));

		AdmissionPayment payment = admissionPaymentRepository.findByCandidateId(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException(
						"El candidato no tiene ficha de pago: " + candidateId));

		if (payment.getPaymentStatus() == AdmissionPaymentStatus.PAID) {
			throw new CandidateAlreadyPaidException(
					"La ficha del candidato " + candidate.getFolio() + " ya estaba pagada.");
		}

		if (orderId == null || orderId.isBlank()) {
			// Mandatory since the window-payment path was removed: without it
			// there is nothing to verify against EVO.
			throw new InvalidPaymentVerificationException(
					"El identificador del pedido es obligatorio para confirmar el pago.");
		}

		CheckoutAttempt attempt = requireOwnOpenAttempt(candidateId, orderId);

		// The ATTEMPT's order is the source of truth, not the ficha's: `orderId` on the
		// payment is overwritten by every retry, so after a second checkout the first
		// attempt's order is gone from that column while its row — and any money
		// captured against it — still exist.
		EvoPaymentsGatewayPort.EvoOrderStatus status = evoPaymentsGateway.retrieveOrder(orderId);

		// CAPTURED, not SUCCESS. `SUCCESS` is a label the gateway puts on an order;
		// `totalCapturedAmount > 0` is money that actually moved. §6 already decided
		// that capture outranks the verdict, and this is the other end of the same
		// rule: an order that says SUCCESS and captured nothing is not a payment.
		if (OrderSettlementDecider.decide(status) != OrderSettlementDecider.Verdict.CAPTURED) {
			throw new InvalidPaymentVerificationException(
					"El pago no fue confirmado por el procesador, inténtalo de nuevo.");
		}

		warnIfUnderpaid(candidate.getFolio(), attempt, status);

		ConfirmPaymentResult result = confirmAdmissionPaymentUseCase.confirm(candidateId);
		checkoutSlotClaimer.closeAttempt(orderId, CheckoutAttemptCloseReason.CAPTURED);
		return result;
	}

	/**
	 * The {@code orderId} must be an attempt this candidate opened and has not settled.
	 *
	 * <p>This replaced an equality check against {@code payment.orderId}, and the swap
	 * looks like a loosening until you see what it enables. Two real situations were
	 * unanswerable before: the applicant pays, the browser re-enters checkout before the
	 * confirmation lands, and the ficha's column now holds the <em>second</em> order
	 * while the first is the one that captured; and the nightly sweep, whose entire job
	 * is to settle attempts that are no longer the live order. Both arrive here with a
	 * legitimate order of this ficha.
	 *
	 * <p>It is not a weakening. The candidate cannot name an order that is not their own
	 * — it has to be a row in {@code checkout_attempt} carrying their candidate id — so
	 * the worst a caller can do is settle their own ficha earlier than they would
	 * otherwise, and only with money the bank confirms they paid. Requiring the attempt
	 * to still be <em>open</em> is what keeps this from re-confirming: a closed attempt
	 * has already been accounted for.
	 */
	private CheckoutAttempt requireOwnOpenAttempt(UUID candidateId, String orderId) {
		CheckoutAttempt attempt = checkoutAttemptRepository.findByOrderId(orderId)
				.orElseThrow(() -> new InvalidPaymentVerificationException(
						"El identificador del pedido no corresponde a esta ficha, inténtalo de nuevo."));
		if (!candidateId.equals(attempt.getCandidateId()) || !attempt.isOpen()) {
			throw new InvalidPaymentVerificationException(
					"El identificador del pedido no corresponde a esta ficha, inténtalo de nuevo.");
		}
		return attempt;
	}

	/**
	 * The capture is compared against the price <em>this attempt</em> asked for, and a
	 * shortfall is logged rather than refused.
	 *
	 * <p>Not refused because the alternative is worse than being underpaid. The money is
	 * at the bank and cannot be clawed back: EVO orders are immutable once created. A
	 * ficha left {@code PENDING} with cash captured is a ficha whose owner will press
	 * "Pagar" again and pay twice, which is exactly the outcome this whole reconciliation
	 * exists to prevent. The tariff is re-quoted on every checkout, so a gap is only
	 * reachable when the catalog moved between two attempts — an operational fact for
	 * the office to reconcile, not a reason to withhold a paid admission.
	 */
	private void warnIfUnderpaid(String folio, CheckoutAttempt attempt,
			EvoPaymentsGatewayPort.EvoOrderStatus status) {
		if (status.amount() == null || attempt.getAmount() == null) {
			return;
		}
		if (status.amount().compareTo(attempt.getAmount()) != 0) {
			log.warn("Pago de {} capturado por {} contra un intento de {} (tarifa vigente al abrirlo). "
					+ "Se marca pagada y se deja la diferencia para revisión.", folio, status.amount(),
					attempt.getAmount());
		}
	}
}