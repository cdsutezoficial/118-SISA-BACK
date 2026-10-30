package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;

import mx.edu.utez.sisa.admission.domain.port.in.InitiateFichaPaymentUseCase;

import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;
import mx.edu.utez.sisa.admission.shared.exception.FichaPaymentConceptNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.FichaPaymentExpiredException;
import mx.edu.utez.sisa.admission.shared.exception.PaymentConceptExpiredException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigNotFoundException;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttemptCloseReason;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Clock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * <li>the two date gates, in order, must both pass (409 each): the ficha's
 * payment window — the earlier of its own deadline and the admission process'
 * closing date ({@code FichaPaymentExpiredException}) — and the tuition concept's
 * availability window
 * ({@code PaymentConceptExpiredException} / {@code FichaPaymentConceptNotFoundException}).
 * Checked <em>before</em> the gateway is called — see
 * {@link #requirePaymentWindowOpen};</li>
 * <li>the gateway {@code INITIATE_CHECKOUT} must produce a session (failure →
 * {@code EvoPaymentGatewayException} → 502).</li>
 * </ol>
 *
 * <p>The order description is "Ficha de Admision {folio}" and the amount is the
 * live tariff the catalog quotes for the program today — the single source of
 * truth, never a client-supplied value (the request body only carries an
 * optional, allowlisted {@code returnPath}). It is re-quoted on every checkout
 * and overwrites the registration quote on the ficha, so a tariff edited
 * between issuing and paying reaches the applicant (§1.3).
 *
 * <p>The description is deliberately unaccented. It is the text the cardholder
 * reads on their bank statement, and a real capture came back as
 * {@code "Ficha de Admisión ADM-2026-000003"} — the accented "ó" encoded as
 * UTF-8 and decoded as Latin-1 somewhere between here and the gateway. We send
 * {@code APPLICATION_JSON} without a charset, so we are not the side declaring
 * an encoding, and this is not worth fighting the processor over: the folio
 * already identifies the ficha uniquely, so dropping the accent costs no
 * information and removes the whole class of bug. {@code OrderDescriptionIsAscii}
 * pins it.
 */
public class InitiateFichaPaymentUseCaseImpl implements InitiateFichaPaymentUseCase {

	/**
	 * What an applicant is told when the tuition concept is not payable today for
	 * a reason that is not the calendar: no concept configured, or none with a
	 * rate. It is a catalog problem, so it points at the office instead of at a
	 * date that does not exist.
	 */
	private static final String CONCEPT_NOT_CONFIGURED_MESSAGE =
			"No se encontró pago vigente configurado para este proceso. Comunícate con Servicios escolares.";

	private final CandidateRepository candidateRepository;

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

	private final FichaPaymentPort fichaPaymentPort;

	/**
	 * Supplies "today" for that window check. Injected, and zone-pinned by
	 * {@code UseCaseConfig}, because a period boundary decided from the server's
	 * default zone is a date the applicant cannot see anywhere on screen.
	 */
	private final Clock clock;

	/**
	 * Days from the registration date (day 0) until the ficha stops being
	 * payable, from {@code sisa.admission.payment.deadline-days}. The deadline is
	 * derived, never stored, so changing this value re-reads every ficha.
	 */
	private final int fichaDeadlineDays;

	/**
	 * Takes and gives back the program's quota slot around the gateway call, in
	 * its own short transactions. A collaborator rather than an inline step
	 * because {@code REQUIRES_NEW} only works through a Spring proxy, so the claim
	 * has to be committed by a different bean than the one calling the gateway.
	 */
	private final CheckoutSlotClaimer checkoutSlotClaimer;

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
			EvoPaymentsGatewayPort evoPaymentsGateway,
			OrderIdBuilder orderIdBuilder, String currency, String returnUrl, String cancelUrl, String checkoutJsUrl,
			Set<String> allowedReturnPaths, ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort,
			FichaAmountResolver fichaAmountResolver, FichaPaymentPort fichaPaymentPort,
			Clock clock, int fichaDeadlineDays,
			CheckoutSlotClaimer checkoutSlotClaimer) {
		this.candidateRepository = candidateRepository;

		this.evoPaymentsGateway = evoPaymentsGateway;
		this.orderIdBuilder = orderIdBuilder;
		this.currency = currency;
		this.returnUrl = returnUrl;
		this.cancelUrl = cancelUrl;
		this.checkoutJsUrl = checkoutJsUrl;
		this.allowedReturnPaths = allowedReturnPaths == null ? Set.of() : Set.copyOf(allowedReturnPaths);
		this.programAdmissionConfigQueryPort = programAdmissionConfigQueryPort;
		this.fichaAmountResolver = fichaAmountResolver;
		this.fichaPaymentPort = fichaPaymentPort;

		this.clock = clock;
		this.fichaDeadlineDays = fichaDeadlineDays;
		this.checkoutSlotClaimer = checkoutSlotClaimer;
	}

	/**
	 * Starts the payment for a ficha.
	 *
	 * <p>Deliberately <b>not</b> {@code @Transactional}. This method spans a call
	 * to a third-party payment gateway, and holding a database transaction open
	 * across it is how a slow provider turns into held locks, long-running
	 * connections and rollbacks of work that was already fine. Each write here
	 * therefore gets its own short transaction, owned by {@link CheckoutSlotClaimer},
	 * and nothing is open while Evo is being contacted.
	 *
	 * <p>Order of operations, and each step is placed for a reason:
	 * <ol>
	 * <li>validate the ficha exists and is unpaid (no locks, cheapest checks
	 * first);</li>
	 * <li>{@code requirePaymentWindowOpen} — before the gateway, because after it
	 * a closed period would need a refund to undo;</li>
	 * <li><b>generate the {@code orderId}</b> — before any write, because it is
	 * what makes the attempt recordable;</li>
	 * <li>{@link CheckoutSlotClaimer#claim} — under a row lock, checks the quota
	 * and opens the checkout attempt with the frozen price, and
	 * <em>commits</em>: the open attempt is what holds the place (15 min), so
	 * there is never a held place without an order id the bank can be asked
	 * about (§3.6), and the lock is not held while Evo is called;</li>
	 * <li>the gateway call;</li>
	 * <li>{@link CheckoutSlotClaimer#persistCheckoutSession} — the order ids the
	 * confirmation will look up.</li>
	 * </ol>
	 *
	 * <p>On a gateway refusal the attempt is closed (which hands the place back) as
	 * {@code ORDER_NOT_CREATED}, but only when the failure proves no order was
	 * created. An ambiguous failure keeps both: Evo may hold an order that is
	 * captured later, and that ficha's payment is real money whether or not we got
	 * a response — the attempt stays open and the daily sweep settles it by asking
	 * the bank.
	 */
	@Override
	public InitiateCheckoutResult initiateCheckout(UUID candidateId, String returnPath) {
		Candidate candidate = candidateRepository.findById(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException("No existe el candidato: " + candidateId));

		if (candidate.getProgramChangePaymentId() != null) {
			return initiateProgramChangeCheckout(candidateId, candidate, returnPath);
		}

		FichaPaymentPort.FichaCharge charge = FichaCharges.of(candidate, fichaPaymentPort)
				.orElseThrow(() -> new CandidateNotFoundException(
						"El candidato no tiene ficha de pago: " + candidateId));

		if (charge.isPaid()) {
			throw new CandidateAlreadyPaidException(
					"La ficha del candidato " + candidate.getFolio() + " ya estaba pagada.");
		}

		ProgramAdmissionConfigQueryPort.AdmissionConfigInfo config = requirePaymentWindowOpen(candidate);

		// The price is computed again right here, at the click — never taken from
		// what a screen showed: the concept's tariff in force now minus the
		// discount in force now. finance photographs it on the ficha's charge
		// (and commits) before anything else happens, and that exact amount is
		// what EVO is asked for.
		FichaPaymentPort.FichaQuote price = fichaPaymentPort.freeze(charge.financePaymentId());

		// A discount that covers the whole tariff (100 %) settles the ficha with
		// no gateway: nothing is owed, so opening an EVO session would only ask
		// for $0. The ficha is closed as exempt, with its $0 receipt linked to
		// the discount.
		if (price.exempt()) {
			checkoutSlotClaimer.settleExempt(candidateId, price);
			return new InitiateCheckoutResult(candidateId, null, null, null, null, checkoutJsUrl, true);
		}

		BigDecimal amount = price.amountDue();

		// The orderId is generated before anything is written, and the attempt that
		// carries it is committed before the gateway is called (§3.6). Generating it
		// after the claim is what used to leave the "slot held with no order number"
		// hole: Evo could hold a capturable order while we had nothing to ask about.
		String orderId = orderIdBuilder.build(candidate.getFolio());
		checkoutSlotClaimer.claim(candidateId, orderId, price);

		return openEvoSession(candidateId, orderId, charge.referenceNumber(), orderDescription(candidate.getFolio()),
				amount, returnPath);
	}

	/**
	 * The difference a career change left to pay, after the ficha was paid. Its
	 * price was fixed at the change, so nothing is re-quoted: EVO is asked for
	 * what is left. The ficha's own windows do not apply — only the deadline the
	 * change gave it.
	 */
	private InitiateCheckoutResult initiateProgramChangeCheckout(UUID candidateId, Candidate candidate,
			String returnPath) {
		FichaPaymentPort.ProgramChangeCharge charge = fichaPaymentPort
				.findProgramChangeCharge(candidate.getProgramChangePaymentId())
				.orElseThrow(() -> new CandidateNotFoundException(
						"El candidato no tiene diferencia de pago: " + candidateId));
		if (charge.paid() || charge.toPay().signum() == 0) {
			throw new CandidateAlreadyPaidException(
					"La diferencia del candidato " + candidate.getFolio() + " ya estaba pagada.");
		}
		if (charge.dueAt() != null && Instant.now(clock).isAfter(charge.dueAt())) {
			throw new FichaPaymentExpiredException(
					"El plazo para pagar la diferencia por cambio de carrera venció. Comunícate con Servicios Escolares.");
		}

		String orderId = orderIdBuilder.build(candidate.getFolio());
		checkoutSlotClaimer.claimProgramChange(candidateId, orderId, charge.financePaymentId(), charge.toPay());
		return openEvoSession(candidateId, orderId, charge.referenceNumber(),
				programChangeOrderDescription(candidate.getFolio()), charge.toPay(), returnPath);
	}

	/** Asks EVO for the hosted session of an attempt already committed. */
	private InitiateCheckoutResult openEvoSession(UUID candidateId, String orderId, String reference,
			String description, BigDecimal amount, String returnPath) {
		String returnUrl = resolveReturnUrl(returnPath);
		EvoPaymentsGatewayPort.EvoOrder order = new EvoPaymentsGatewayPort.EvoOrder(orderId, reference, description,
				amount, currency, withCheckoutParams(returnUrl, candidateId, orderId),
				withCheckoutParams(resolveCancelUrl(returnPath, returnUrl), candidateId, orderId));

		EvoPaymentsGatewayPort.EvoSession session;
		try {
			session = evoPaymentsGateway.initiateCheckoutSession(order);
		} catch (EvoPaymentGatewayException ex) {
			if (!ex.orderMayHaveBeenCreated()) {
				checkoutSlotClaimer.closeAttempt(orderId, CheckoutAttemptCloseReason.ORDER_NOT_CREATED);
			}
			throw ex;
		}

		checkoutSlotClaimer.persistCheckoutSession(orderId, session.id());

		return new InitiateCheckoutResult(candidateId, orderId, session.id(), session.merchant(),
				session.successIndicator(), checkoutJsUrl, false);
	}

	/**
	 * Refuses to start a checkout when any of the three date gates has closed,
	 * and does it <em>before</em> the gateway is touched.
	 *
	 * <p>That ordering is the whole point of the method. The gates are checked at
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
	 * <p>The gates run in order, and the order is the rule:
	 * <ol>
	 * <li><b>the ficha's payment window</b> — the earlier of its own deadline
	 * (day 0 = registration, plus {@code deadline-days}) and the admission
	 * process' closing date. Both bounds are the same ones the nightly expiry
	 * sweep and the occupancy queries read, so a ficha the portal shows as
	 * payable cannot be one the engine considers closed;</li>
	 * <li><b>the tuition concept's window</b>, which is configuration and must
	 * not be reported as a date the applicant can see; a concept that is missing
	 * or not payable here is a catalog error, not a closed period.</li>
	 * </ol>
	 *
	 * <p>Gate 0 used to be the ficha's own deadline alone and gate 1 the process
	 * closing date, reported as two distinct errors. That distinction was a lie
	 * the applicant could not act on: the process closing means the same thing as
	 * her window closing, and one of them always arrived first. Both now report
	 * the ficha's window as expired, which is the fact she can verify.
	 *
	 * <p>Every boundary is a calendar date in the admission zone, not an
	 * {@code Instant}: a comparison at midnight UTC would refuse a ficha that is
	 * still payable by the applicant's calendar (see {@code §10.2}).
	 *
	 * <p>Prices nothing: it only decides whether the window is open. The caller
	 * prices the checkout separately with the returned config, because the
	 * amount that governs is the live tariff at the click, not the quote frozen
	 * at registration.
	 *
	 * @return the candidate's admission config, so the caller can re-quote the
	 *         tariff for the same program without resolving it twice
	 */
	private ProgramAdmissionConfigQueryPort.AdmissionConfigInfo requirePaymentWindowOpen(Candidate candidate) {
		ProgramAdmissionConfigQueryPort.AdmissionConfigInfo config = programAdmissionConfigQueryPort
				.findById(candidate.getAdmissionConfigId())
				.orElseThrow(() -> new ProgramAdmissionConfigNotFoundException(
						"No existe la configuración de admisión: " + candidate.getAdmissionConfigId()));

		LocalDate today = LocalDate.now(clock);
		ZoneId zone = clock.getZone();

		// Gate 0 — the ficha's payment window (own plazo ∩ process closing date).
		LocalDate admissionClosesOn = config.closesAt().atZone(zone).toLocalDate();
		if (today.isAfter(FichaPaymentWindow.deadlineOf(candidate, admissionClosesOn, fichaDeadlineDays, zone))) {
			throw new FichaPaymentExpiredException("Tu ficha venció. El plazo de pago de " + fichaDeadlineDays
					+ (fichaDeadlineDays == 1 ? " día" : " días") + " terminó.");
		}

		// Gate 2 — the tuition concept exists and is payable today.
		requireConceptWindowOpen(config, today);

		return config;
	}

	/**
	 * The concept window, reworded for the admission flow. The resolver reports
	 * "no concept" and "concept outside its window" with distinct types and
	 * catalog-facing dates; by the time the checkout reaches here both mean the
	 * same thing to the applicant — admissions has no payable tuition configured
	 * for her program — so both keep their type (and therefore their 409 code)
	 * and take the office-facing message instead of a date that would send her to
	 * look at a screen she cannot fix.
	 */
	private void requireConceptWindowOpen(ProgramAdmissionConfigQueryPort.AdmissionConfigInfo config, LocalDate today) {
		try {
			fichaAmountResolver.requirePayableOn(config.programId(), today);
		} catch (PaymentConceptExpiredException ex) {
			throw new PaymentConceptExpiredException(CONCEPT_NOT_CONFIGURED_MESSAGE);
		} catch (FichaPaymentConceptNotFoundException ex) {
			throw new FichaPaymentConceptNotFoundException(CONCEPT_NOT_CONFIGURED_MESSAGE);
		}
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

	/**
	 * The order description the bank shows on the cardholder's statement.
	 *
	 * <p>ASCII on purpose. A real 3DS capture in the sandbox came back from the
	 * gateway as {@code "Ficha de Admisión ADM-2026-000003"}: the accented "ó"
	 * went out as UTF-8 and came back decoded as Latin-1, so the applicant would
	 * have seen mojibake on their bank statement. The request goes out as
	 * {@code APPLICATION_JSON} with no charset declared, so this side is not the
	 * one declaring an encoding and there is nothing to fix here beyond not
	 * depending on the processor honouring ours. The folio already identifies the
	 * ficha, so dropping the accent costs no information.
	 */
	static String orderDescription(String folio) {
		return "Ficha de Admision " + folio;
	}

	static String programChangeOrderDescription(String folio) {
		return "Diferencia cambio de carrera " + folio;
	}

	private static String withCheckoutParams(String baseUrl, UUID candidateId, String orderId) {
		if (baseUrl == null || baseUrl.isBlank()) {
			return baseUrl;
		}
		return UriComponentsBuilder.fromUriString(baseUrl).queryParam("id", candidateId.toString())
				.queryParam("orderId", orderId).build().toUriString();
	}
}
