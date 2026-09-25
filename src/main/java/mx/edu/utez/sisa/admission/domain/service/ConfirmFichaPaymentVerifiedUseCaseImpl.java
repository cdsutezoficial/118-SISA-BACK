package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase.ConfirmPaymentResult;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmFichaPaymentVerifiedUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.InvalidPaymentVerificationException;
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
 * <li>the ficha never initiated online (no persisted {@code orderId}) and the
 * caller sends no {@code orderId} → legacy window confirm
 * ({@link ConfirmAdmissionPaymentUseCase}, Finanzas); with an {@code orderId}
 * → 400 (no session exists to verify);</li>
 * <li>an online ficha whose returned {@code orderId} does not match the
 * persisted one → 400;</li>
 * <li>{@code RETRIEVE_ORDER} must give {@code SUCCESS} with the same amount
 * → 400 otherwise;</li>
 * <li>only then the transition happens through the existing
 * {@link ConfirmAdmissionPaymentUseCase} (receipt, candidate {@code PAID},
 * idempotent 409).</li>
 * </ol>
 */
public class ConfirmFichaPaymentVerifiedUseCaseImpl implements ConfirmFichaPaymentVerifiedUseCase {

	private final CandidateRepository candidateRepository;

	private final AdmissionPaymentRepository admissionPaymentRepository;

	private final EvoPaymentsGatewayPort evoPaymentsGateway;

	private final ConfirmAdmissionPaymentUseCase confirmAdmissionPaymentUseCase;

	public ConfirmFichaPaymentVerifiedUseCaseImpl(CandidateRepository candidateRepository,
			AdmissionPaymentRepository admissionPaymentRepository, EvoPaymentsGatewayPort evoPaymentsGateway,
			ConfirmAdmissionPaymentUseCase confirmAdmissionPaymentUseCase) {
		this.candidateRepository = candidateRepository;
		this.admissionPaymentRepository = admissionPaymentRepository;
		this.evoPaymentsGateway = evoPaymentsGateway;
		this.confirmAdmissionPaymentUseCase = confirmAdmissionPaymentUseCase;
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

		boolean onlineInitiated = payment.getOrderId() != null && !payment.getOrderId().isBlank();
		if (!onlineInitiated && (orderId == null || orderId.isBlank())) {
			return confirmAdmissionPaymentUseCase.confirm(candidateId);
		}
		if (!onlineInitiated) {
			throw new InvalidPaymentVerificationException(
					"Esta ficha no tiene un pago en línea iniciado, no hay sesión que verificar.");
		}
		if (orderId != null && !orderId.isBlank() && !payment.getOrderId().equals(orderId)) {
			throw new InvalidPaymentVerificationException(
					"El identificador del pedido no corresponde a esta ficha, inténtalo de nuevo.");
		}

		// The PERSISTED order is the source of truth (the body's orderId, when sent,
		// only cross-checks that the applicant returned to the right ficha).
		EvoPaymentsGatewayPort.EvoOrderStatus status = evoPaymentsGateway.retrieveOrder(payment.getOrderId());
		if (!"SUCCESS".equals(status.result())) {
			throw new InvalidPaymentVerificationException(
					"El pago no fue confirmado por el procesador, inténtalo de nuevo.");
		}
		if (status.amount() == null || status.amount().compareTo(payment.getAmount()) != 0) {
			throw new InvalidPaymentVerificationException(
					"El monto del pago registrado por el procesador no corresponde a la ficha.");
		}

		return confirmAdmissionPaymentUseCase.confirm(candidateId);
	}
}