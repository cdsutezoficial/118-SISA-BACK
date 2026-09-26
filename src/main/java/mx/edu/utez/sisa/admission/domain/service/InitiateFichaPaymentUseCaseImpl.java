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
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Set;
import java.util.UUID;

/**
 * Checkout interactor (design: {@code 03-admision.md}, Fase 4 of the payment
 * plan): the web port / application service that starts the online payment for
 * a ficha against the EVO gateway. Plain, framework-agnostic — the gateway
 * goes through the {@link EvoPaymentsGatewayPort} out-port and the hosted
 * checkout SDK URL comes from the configuration injected at the composition
 * root ({@code UseCaseConfig}).
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
 * client-supplied value (the request body only carries an optional, allowlisted
 * {@code returnPath}).
 */
public class InitiateFichaPaymentUseCaseImpl implements InitiateFichaPaymentUseCase {

	private final CandidateRepository candidateRepository;

	private final AdmissionPaymentRepository admissionPaymentRepository;

	private final EvoPaymentsGatewayPort evoPaymentsGateway;

	private final OrderIdBuilder orderIdBuilder;

	private final String currency;

	private final String returnUrl;

	private final String cancelUrl;

	private final String checkoutJsUrl;

	/**
	 * In-app paths a caller may ask the gateway to return to. Anything else falls
	 * back to {@link #returnUrl}, so a bug (or an attacker) cannot redirect a
	 * payer off-origin.
	 */
	private final Set<String> allowedReturnPaths;

	public InitiateFichaPaymentUseCaseImpl(CandidateRepository candidateRepository,
			AdmissionPaymentRepository admissionPaymentRepository, EvoPaymentsGatewayPort evoPaymentsGateway,
			OrderIdBuilder orderIdBuilder, String currency, String returnUrl, String cancelUrl, String checkoutJsUrl) {
		this(candidateRepository, admissionPaymentRepository, evoPaymentsGateway, orderIdBuilder, currency, returnUrl,
				cancelUrl, checkoutJsUrl, Set.of());
	}

	public InitiateFichaPaymentUseCaseImpl(CandidateRepository candidateRepository,
			AdmissionPaymentRepository admissionPaymentRepository, EvoPaymentsGatewayPort evoPaymentsGateway,
			OrderIdBuilder orderIdBuilder, String currency, String returnUrl, String cancelUrl, String checkoutJsUrl,
			Set<String> allowedReturnPaths) {
		this.candidateRepository = candidateRepository;
		this.admissionPaymentRepository = admissionPaymentRepository;
		this.evoPaymentsGateway = evoPaymentsGateway;
		this.orderIdBuilder = orderIdBuilder;
		this.currency = currency;
		this.returnUrl = returnUrl;
		this.cancelUrl = cancelUrl;
		this.checkoutJsUrl = checkoutJsUrl;
		this.allowedReturnPaths = allowedReturnPaths == null ? Set.of() : Set.copyOf(allowedReturnPaths);
	}

	@Override
	@Transactional
	public InitiateCheckoutResult initiateCheckout(UUID candidateId, String returnPath) {
		Candidate candidate = candidateRepository.findById(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException("No existe el candidato: " + candidateId));

		AdmissionPayment payment = admissionPaymentRepository.findByCandidateId(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException(
						"El candidato no tiene ficha de pago: " + candidateId));

		if (payment.getPaymentStatus() == AdmissionPaymentStatus.PAID) {
			throw new CandidateAlreadyPaidException(
					"La ficha del candidato " + candidate.getFolio() + " ya estaba pagada.");
		}

		String returnUrl = resolveReturnUrl(returnPath);
		String orderId = orderIdBuilder.build(candidate.getFolio());
		EvoPaymentsGatewayPort.EvoOrder order = new EvoPaymentsGatewayPort.EvoOrder(orderId,
				payment.getReferenceNumber(), "Ficha de Admisión " + candidate.getFolio(), payment.getAmount(),
				currency, withCheckoutParams(returnUrl, candidateId, orderId),
				withCheckoutParams(resolveCancelUrl(returnPath, returnUrl), candidateId, orderId));
		EvoPaymentsGatewayPort.EvoSession session = evoPaymentsGateway.initiateCheckoutSession(order);

		payment.registerCheckout(orderId, session.id());
		admissionPaymentRepository.save(payment);

		return new InitiateCheckoutResult(candidateId, orderId, session.id(), session.merchant(),
				session.successIndicator(), checkoutJsUrl);
	}

	/**
	 * Applies an allowlisted {@code returnPath} by swapping only the PATH of the
	 * configured return URL. The scheme and host always come from configuration,
	 * never from the request, so even a leaked or mis-configured allowlist entry
	 * cannot aim a payer at another origin.
	 */
	private String resolveReturnUrl(String requestedPath) {
		if (requestedPath == null || requestedPath.isBlank() || !allowedReturnPaths.contains(requestedPath)) {
			return returnUrl;
		}
		return UriComponentsBuilder.fromUriString(returnUrl).replacePath(requestedPath).build().toUriString();
	}

	/**
	 * The cancel URL has to follow the request too. Cancelling is an ordinary
	 * outcome inside a bank's hosted flow, and leaving it pinned to the globally
	 * configured page would drop a payer who started from the weaker "folio +
	 * CURP tail" lookup onto a screen the backend would not have shown them
	 * otherwise. The scheme/host still come from {@link #cancelUrl} when it is
	 * configured, falling back to the resolved return URL.
	 */
	private String resolveCancelUrl(String requestedPath, String resolvedReturnUrl) {
		if (requestedPath == null || requestedPath.isBlank() || !allowedReturnPaths.contains(requestedPath)) {
			return cancelUrl;
		}
		String base = (cancelUrl == null || cancelUrl.isBlank()) ? resolvedReturnUrl : cancelUrl;
		if (base == null || base.isBlank()) {
			return base;
		}
		return UriComponentsBuilder.fromUriString(base).replacePath(requestedPath).build().toUriString();
	}

	private static String withCheckoutParams(String baseUrl, UUID candidateId, String orderId) {
		if (baseUrl == null || baseUrl.isBlank()) {
			return baseUrl;
		}
		return UriComponentsBuilder.fromUriString(baseUrl).queryParam("id", candidateId.toString())
				.queryParam("orderId", orderId).build().toUriString();
	}
}
