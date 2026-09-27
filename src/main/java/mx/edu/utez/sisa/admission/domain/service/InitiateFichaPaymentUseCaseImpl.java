package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.InitiateFichaPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.LocalDate;
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
 * <li>the tuition concept's availability window must contain today (409,
 * {@code PaymentConceptExpiredException}, or
 * {@code FichaPaymentConceptNotFoundException} when the program has no tuition
 * concept at all). Checked <em>before</em> the gateway is called — see
 * {@link #requireConceptWindowOpen};</li>
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

	/**
	 * Resolves the candidate's program so the tuition concept's window can be
	 * re-checked at payment time, and prices nothing.
	 */
	private final ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	private final FichaAmountResolver fichaAmountResolver;

	/**
	 * Supplies "today" for that window check. Injected, and zone-pinned by
	 * {@code UseCaseConfig}, because a period boundary decided from the server's
	 * default zone is a date the applicant cannot see anywhere on screen.
	 */
	private final Clock clock;

	/**
	 * A single constructor, deliberately.
	 *
	 * <p>This class used to offer shorter overloads. When the concept-window check
	 * was added they would have had to default its three collaborators to
	 * {@code null}, which means a caller could construct a checkout interactor
	 * that either throws {@code NullPointerException} on the first payment or —
	 * worse, with a null guard — silently accepts payments outside the period the
	 * catalog says are closed. A rule that can be forgotten by choosing a
	 * constructor is not a rule. Every dependency is required, so the only way to
	 * build this is to decide what "today" means and where the concept lives.
	 */
	public InitiateFichaPaymentUseCaseImpl(CandidateRepository candidateRepository,
			AdmissionPaymentRepository admissionPaymentRepository, EvoPaymentsGatewayPort evoPaymentsGateway,
			OrderIdBuilder orderIdBuilder, String currency, String returnUrl, String cancelUrl, String checkoutJsUrl,
			Set<String> allowedReturnPaths, ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort,
			FichaAmountResolver fichaAmountResolver, Clock clock) {
		this.candidateRepository = candidateRepository;
		this.admissionPaymentRepository = admissionPaymentRepository;
		this.evoPaymentsGateway = evoPaymentsGateway;
		this.orderIdBuilder = orderIdBuilder;
		this.currency = currency;
		this.returnUrl = returnUrl;
		this.cancelUrl = cancelUrl;
		this.checkoutJsUrl = checkoutJsUrl;
		this.allowedReturnPaths = allowedReturnPaths == null ? Set.of() : Set.copyOf(allowedReturnPaths);
		this.programAdmissionConfigQueryPort = programAdmissionConfigQueryPort;
		this.fichaAmountResolver = fichaAmountResolver;
		this.clock = clock;
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

		requireConceptWindowOpen(candidate);

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
	 * Refuses to start a checkout outside the tuition concept's availability
	 * window, and does it <em>before</em> the gateway is touched.
	 *
	 * <p>That ordering is the whole point of the method. The window is checked at
	 * registration too, but registration and payment are days apart and the
	 * catalog can change in between — extending a period is how staff normally
	 * handle a late applicant, and closing it is how they close a cohort. A
	 * ticket issued on the last open day would otherwise stay payable for as long
	 * as the applicant kept clicking, which is exactly the contradiction this
	 * block exists to remove.
	 *
	 * <p>Cutting before the gateway call is what makes a closed period free. Once
	 * {@code INITIATE_CHECKOUT} has run, Evo has an order in {@code PENDING} that
	 * it will remember, and re-opening the period would then hit the duplicate
	 * order rejection from {@code §2.3} of the plan on an id we already burned.
	 *
	 * <p>The amount is not consulted here and never re-priced: what the applicant
	 * pays is still {@code payment.getAmount()}, frozen at registration.
	 */
	private void requireConceptWindowOpen(Candidate candidate) {
		ProgramAdmissionConfigQueryPort.AdmissionConfigInfo config = programAdmissionConfigQueryPort
				.findById(candidate.getAdmissionConfigId())
				.orElseThrow(() -> new ProgramAdmissionConfigNotFoundException(
						"No existe la configuración de admisión: " + candidate.getAdmissionConfigId()));
		fichaAmountResolver.requirePayableOn(config.programId(), LocalDate.now(clock));
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
