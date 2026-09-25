package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.InitiateFichaPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Checkout interactor (design: {@code 03-admision.md}, Fase 4 of the payment
 * plan): the web port / application service that starts the online payment for
 * a ficha against the EVO gateway. Plain, framework-agnostic — the gateway
 * goes through the {@link EvoPaymentsGatewayPort} out-port and the checkout
 * page URL is built from the config values injected at the composition root
 * ({@code UseCaseConfig}).
 *
 * <p>Validations, in order:
 * <ol>
 * <li>the {@code candidateId} must resolve to a {@code Candidate} and its
 * {@code ADMISSION_FICHA} payment must exist (404,
 * {@code CandidateNotFoundException});</li>
 * <li>the payment must still be {@code PENDING} (409,
 * {@code CandidateAlreadyPaidException}) — an already-paid ficha cannot start
 * a new online session;</li>
 * <li>the gateway {@code INITIATE_CHECKOUT} must produce a session (failure →
 * {@code EvoPaymentGatewayException} → 502).</li>
 * </ol>
 *
 * <p>The order description is "Ficha de Admisión {folio}" and the amount comes
 * from the payment concept itself — the single source of truth, never a
 * client-supplied value (there is no request body).
 */
public class InitiateFichaPaymentUseCaseImpl implements InitiateFichaPaymentUseCase {

	private final CandidateRepository candidateRepository;

	private final AdmissionPaymentRepository admissionPaymentRepository;

	private final EvoPaymentsGatewayPort evoPaymentsGateway;

	private final OrderIdBuilder orderIdBuilder;

	private final String currency;

	private final String returnUrl;

	private final String cancelUrl;

	private final String paymentPageBaseUrl;

	private final String paymentPageVersion;

	public InitiateFichaPaymentUseCaseImpl(CandidateRepository candidateRepository,
			AdmissionPaymentRepository admissionPaymentRepository, EvoPaymentsGatewayPort evoPaymentsGateway,
			OrderIdBuilder orderIdBuilder, String currency, String returnUrl, String cancelUrl,
			String paymentPageBaseUrl, String paymentPageVersion) {
		this.candidateRepository = candidateRepository;
		this.admissionPaymentRepository = admissionPaymentRepository;
		this.evoPaymentsGateway = evoPaymentsGateway;
		this.orderIdBuilder = orderIdBuilder;
		this.currency = currency;
		this.returnUrl = returnUrl;
		this.cancelUrl = cancelUrl;
		this.paymentPageBaseUrl = paymentPageBaseUrl;
		this.paymentPageVersion = paymentPageVersion;
	}

	@Override
	@Transactional
	public InitiateCheckoutResult initiateCheckout(UUID candidateId) {
		Candidate candidate = candidateRepository.findById(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException("No existe el candidato: " + candidateId));

		AdmissionPayment payment = admissionPaymentRepository.findByCandidateId(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException(
						"El candidato no tiene ficha de pago: " + candidateId));

		if (payment.getPaymentStatus() == AdmissionPaymentStatus.PAID) {
			throw new CandidateAlreadyPaidException(
					"La ficha del candidato " + candidate.getFolio() + " ya estaba pagada.");
		}

		String orderId = orderIdBuilder.build(candidate.getFolio());
		EvoPaymentsGatewayPort.EvoOrder order = new EvoPaymentsGatewayPort.EvoOrder(orderId,
				payment.getReferenceNumber(), "Ficha de Admisión " + candidate.getFolio(), payment.getAmount(),
				currency, returnUrl, cancelUrl);
		EvoPaymentsGatewayPort.EvoSession session = evoPaymentsGateway.initiateCheckoutSession(order);

		payment.registerCheckout(orderId, session.id());
		admissionPaymentRepository.save(payment);

		return new InitiateCheckoutResult(candidateId, orderId, session.id(), session.version(), session.merchant(),
				session.successIndicator(), checkoutUrl());
	}

	/**
	 * Hosted Checkout payment page. The {@code version} path segment is the EVO
	 * <em>API</em> version (config, same one the REST base URL carries, e.g.
	 * {@code 72}) — NOT the {@code session.version} token the
	 * {@code INITIATE_CHECKOUT} response returns: posting the page with that
	 * token makes the gateway answer {@code "Unsupported value for 'version =
	 * …'"}. The page is reachable only via a form POST carrying
	 * {@code merchant} + {@code session}.
	 */
	private String checkoutUrl() {
		return paymentPageBaseUrl + "/api/page/version/" + paymentPageVersion + "/pay";
	}
}