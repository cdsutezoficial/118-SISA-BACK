package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.admission.domain.port.in.InitiateFichaPaymentUseCase.InitiateCheckoutResult;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionQuotaPort;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.finance.domain.port.out.CheckoutAttemptRepository;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;
import mx.edu.utez.sisa.admission.shared.exception.FichaPaymentConceptNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.FichaPaymentExpiredException;
import mx.edu.utez.sisa.admission.shared.exception.PaymentConceptExpiredException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigCapacityReachedException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InitiateFichaPaymentUseCaseImpl} (Fase 4): a
 * successful checkout builds the EVO order (order.id from the builder, amount
 * from the payment concept, description with the folio), persists
 * {@code order_id} + {@code checkout_session_id} on the payment, returns the
 * Checkout SDK URL and appends the candidate/order to the return URLs;
 * 404/409 guard cases; gateway failure propagates
 * as {@link EvoPaymentGatewayException} (→ 502 at the web layer, handled in
 * {@code CandidateControllerTest}).
 *
 * <p>The builder is mocked so the order id is a fixed string here — how that id
 * is composed is {@link OrderIdBuilderTest}'s business. What matters in this
 * class is that the use case feeds it the folio and then threads the result
 * through the order, the return URLs and the persisted payment, which is what
 * {@link #twoCheckoutsOfTheSameFichaAreTwoDifferentOrders()} pins down.
 */
@ExtendWith(MockitoExtension.class)
class InitiateFichaPaymentUseCaseImplTest {

	private static final String RETURN = "http://localhost:5173/portal/registro/ficha";
	private static final String CANCEL = "http://localhost:5173/portal/registro/ficha";
	private static final String SDK_URL = "https://evopaymentsmexico.gateway.mastercard.com/static/checkout/checkout.min.js";
	private static final String ORDER_ID = "TESTUTEZ-ADM-2026-000001";

	/**
	 * The tariff the catalog quotes at checkout, deliberately different from the
	 * 500.00 the ficha was registered with, so a test can tell the live price
	 * apart from the registration quote.
	 */
	private static final BigDecimal LIVE_AMOUNT = new BigDecimal("550.00");

	/** The charge's live quote at the click: tariff in force, no discount. */
	private static final FichaPaymentPort.FichaQuote LIVE_QUOTE = new FichaPaymentPort.FichaQuote(
			UUID.fromString("00000000-0000-0000-0000-0000000000c1"), "FICHA-ING", "Inscripción", null, LIVE_AMOUNT,
			new BigDecimal("0.00"), LIVE_AMOUNT, null, false, false, null);

	private static final UUID CANDIDATE_ID = UUID.randomUUID();

	private static final UUID ADMISSION_CONFIG_ID = UUID.randomUUID();

	private static final UUID PROGRAM_ID = UUID.randomUUID();

	/**
	 * Fixed so the window check is a fact about the test rather than about the day
	 * it runs, and pinned to the same zone the application configures.
	 */
	private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

	/** Noon on {@link #TODAY} as an instant, so the local date is unambiguous. */
	private static final Instant NOW_AT_NOON = TODAY.atTime(12, 0).atZone(ZONE).toInstant();

	private static final int DEADLINE_DAYS = 10;

	private static final UUID CHARGE_ID = UUID.randomUUID();

	/**
	 * A registration date that puts {@link #TODAY} squarely inside the 10-day
	 * window: day 0 is 2026-09-20, the deadline is 2026-09-30, and the 25th is
	 * payable. Pinned rather than {@code Instant.now()} so the gates are a fact
	 * about the test rather than about the day it runs.
	 */
	private static final Instant REGISTERED_AT = LocalDate.of(2026, 9, 20).atTime(18, 0).atZone(ZONE).toInstant();

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private EvoPaymentsGatewayPort evoPaymentsGateway;

	@Mock
	private OrderIdBuilder orderIdBuilder;

	@Mock
	private ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	@Mock
	private FichaAmountResolver fichaAmountResolver;

	/**
	 * Only consulted by the tests that drive a real {@link CheckoutSlotClaimer}
	 * instead of the mock above.
	 */
	@Mock
	private AdmissionQuotaPort admissionQuotaPort;

	@Mock
	private CheckoutAttemptRepository checkoutAttemptRepository;

	/**
	 * Mocked because the point of this class is the use case's own orchestration —
	 * what it calls, in what order, and what it does with a gateway failure. The
	 * claimer's own locking, counting and commit boundaries are
	 * {@code CheckoutSlotClaimerTest}'s business; driving a real one here would
	 * need a database and would test the wrong unit twice.
	 */
	@Mock
	private CheckoutSlotClaimer checkoutSlotClaimer;

	/** The ficha's finance charge: frozen at the click, settled at confirmation. */
	@Mock
	private FichaPaymentPort fichaPaymentPort;

	private InitiateFichaPaymentUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		// lenient: the 404/409 guard cases throw before the builder is ever consulted
		lenient().when(orderIdBuilder.build(any())).thenReturn(ORDER_ID);
		// lenient: only the paths that clear all gates link and freeze the charge
				lenient().when(fichaPaymentPort.findCharge(CHARGE_ID))
				.thenReturn(Optional.of(FichaTestData.pendingCharge(CHARGE_ID)));
		lenient().when(fichaPaymentPort.freeze(CHARGE_ID)).thenReturn(LIVE_QUOTE);
		// lenient: only the paths that get past the PAID check resolve the config
		lenient().when(programAdmissionConfigQueryPort.findById(ADMISSION_CONFIG_ID))
				.thenReturn(Optional.of(new ProgramAdmissionConfigQueryPort.AdmissionConfigInfo(ADMISSION_CONFIG_ID,
						ProgramAdmissionConfigStatus.OPEN, PROGRAM_ID, "Ingeniería en Software", null, null, null,
						LocalDate.of(2026, 9, 1).atStartOfDay(ZONE).toInstant(),
						LocalDate.of(2026, 9, 30).atStartOfDay(ZONE).toInstant(), 40)));
		useCase = new InitiateFichaPaymentUseCaseImpl(candidateRepository, evoPaymentsGateway, orderIdBuilder, "MXN", RETURN, CANCEL, SDK_URL, Set.of(),
				programAdmissionConfigQueryPort, fichaAmountResolver, fichaPaymentPort, Clock.fixed(NOW_AT_NOON, ZONE),
				DEADLINE_DAYS, checkoutSlotClaimer);
	}

	private static Candidate candidate() {
		return candidateRegisteredAt(REGISTERED_AT);
	}

	private static Candidate candidateRegisteredAt(Instant registeredAt) {
		Candidate candidate = new Candidate(UUID.randomUUID(), ADMISSION_CONFIG_ID, "ADM-2026-000001", true, true, null);
		ReflectionTestUtils.setField(candidate, "registeredAt", registeredAt);
		candidate.linkFichaPayment(CHARGE_ID);
		return candidate;
	}

	private static Candidate candidateRegisteredOn(LocalDate day) {
		return candidateRegisteredAt(day.atTime(18, 0).atZone(ZONE).toInstant());
	}

	private static FichaPaymentPort.FichaCharge payment() {
		return FichaTestData.pendingCharge(CHARGE_ID);
	}

	@Test
	void successfulCheckoutPersistsSessionAndReturnsCheckoutUrl() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(evoPaymentsGateway.initiateCheckoutSession(any())).thenReturn(
				new EvoPaymentsGatewayPort.EvoSession("SESSION0001BR", "TESTUTEZ", "AAAA/BRAVO/SUCCESS0001",
						"df66ca1b01"));

		InitiateCheckoutResult result = useCase.initiateCheckout(CANDIDATE_ID, null);

		assertThat(result.candidateId()).isEqualTo(CANDIDATE_ID);
		assertThat(result.orderId()).isEqualTo(ORDER_ID);
		assertThat(result.sessionId()).isEqualTo("SESSION0001BR");
		assertThat(result.merchant()).isEqualTo("TESTUTEZ");
		assertThat(result.successIndicator()).isEqualTo("AAAA/BRAVO/SUCCESS0001");
		assertThat(result.checkoutJsUrl()).isEqualTo(SDK_URL);

		verify(orderIdBuilder).build("ADM-2026-000001");

		String expectedReturn = RETURN + "?id=" + CANDIDATE_ID + "&orderId=" + ORDER_ID;
		verify(evoPaymentsGateway).initiateCheckoutSession(argThat(order -> ORDER_ID.equals(order.id())
				&& FichaTestData.REFERENCE.equals(order.reference())
				&& LIVE_AMOUNT.compareTo(order.amount()) == 0 && "MXN".equals(order.currency())
				&& "Ficha de Admision ADM-2026-000001".equals(order.description())
				&& expectedReturn.equals(order.returnUrl()) && expectedReturn.equals(order.cancelUrl())));
		// Persisting the session is the claimer's job now, in its own transaction.
		// What this class pins is that the use case hands it exactly the ids Evo
		// returned — a mismatch here would leave the confirmation flow unable to
		// find the order it has to verify.
		verify(checkoutSlotClaimer).persistCheckoutSession(ORDER_ID, "SESSION0001BR");
	}

	/**
	 * The description is the text the cardholder reads on their bank statement, and
	 * a real 3DS capture in the sandbox came back from the gateway as
	 * {@code "Ficha de Admisión ADM-2026-000003"} — the accented "ó" encoded as
	 * UTF-8 and decoded as Latin-1 somewhere along the way.
	 *
	 * <p>This does not pin the exact wording, which is free to change; it pins that
	 * the string is pure ASCII. That is the actual property: the request goes out as
	 * {@code APPLICATION_JSON} with no charset declared, so we are not the side
	 * deciding how it is read, and the only way to stop depending on the processor
	 * is to not send anything it could misread. The folio already identifies the
	 * ficha, so nothing is lost.
	 */
	@Test
	void orderDescriptionIsAsciiSoTheBankStatementCannotShowMojibake() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(evoPaymentsGateway.initiateCheckoutSession(any()))
				.thenReturn(new EvoPaymentsGatewayPort.EvoSession("SESSION0001BR", "TESTUTEZ", "OK", "df66ca1b01"));

		useCase.initiateCheckout(CANDIDATE_ID, null);

		ArgumentCaptor<EvoPaymentsGatewayPort.EvoOrder> order = ArgumentCaptor.forClass(EvoPaymentsGatewayPort.EvoOrder.class);
		verify(evoPaymentsGateway).initiateCheckoutSession(order.capture());

		String description = order.getValue().description();
		assertThat(description).isEqualTo("Ficha de Admision ADM-2026-000001");
		assertThat(description.chars().allMatch(c -> c < 128))
				.as("la descripción que ve el titular en su estado de cuenta debe ser ASCII puro")
				.isTrue();
	}

	/**
	 * The regression that motivated the random suffix: paying the same ficha twice
	 * used to send Evo the very same {@code order.id}, which the gateway refuses
	 * ("payment for this order has already been received") and, worse, which an
	 * applicant could derive from the folio and present as proof of payment. Each
	 * checkout now gets its own id, and the latest one is what the payment keeps.
	 */
	@Test
	void twoCheckoutsOfTheSameFichaAreTwoDifferentOrders() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(evoPaymentsGateway.initiateCheckoutSession(any())).thenReturn(
				new EvoPaymentsGatewayPort.EvoSession("SESSION0001BR", "TESTUTEZ", "OK", "df66ca1b01"));
		InitiateFichaPaymentUseCaseImpl realBuilderUseCase = new InitiateFichaPaymentUseCaseImpl(candidateRepository,
				evoPaymentsGateway, new OrderIdBuilder("TESTUTEZ", 32), "MXN", RETURN,
				CANCEL, SDK_URL, Set.of(), programAdmissionConfigQueryPort, fichaAmountResolver, fichaPaymentPort,
				Clock.fixed(NOW_AT_NOON, ZONE), DEADLINE_DAYS, checkoutSlotClaimer);

		String first = realBuilderUseCase.initiateCheckout(CANDIDATE_ID, null).orderId();
		String second = realBuilderUseCase.initiateCheckout(CANDIDATE_ID, null).orderId();

		assertThat(first).isNotEqualTo(second);
		assertThat(first).startsWith("TESTUTEZ-ADM-2026-000001-");
		assertThat(second).startsWith("TESTUTEZ-ADM-2026-000001-");

		// The second attempt overwrites the first order on the ficha, so the
		// persisted one has to be the latest — otherwise the confirmation would
		// verify an order the applicant already abandoned.
		ArgumentCaptor<String> orderIds = ArgumentCaptor.forClass(String.class);
		verify(checkoutSlotClaimer, times(2))
				.persistCheckoutSession(orderIds.capture(), any());
		assertThat(orderIds.getAllValues().get(1)).isEqualTo(second);
	}

	@Test
	void appendsCandidateAndOrderToExistingReturnUrlQuery() {
		String returnWithQuery = RETURN + "?origen=checkout";
		InitiateFichaPaymentUseCaseImpl customUseCase = new InitiateFichaPaymentUseCaseImpl(candidateRepository,
				evoPaymentsGateway, orderIdBuilder, "MXN", returnWithQuery, CANCEL,
				SDK_URL, Set.of(), programAdmissionConfigQueryPort, fichaAmountResolver, fichaPaymentPort,
				Clock.fixed(NOW_AT_NOON, ZONE), DEADLINE_DAYS, checkoutSlotClaimer);
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(evoPaymentsGateway.initiateCheckoutSession(any())).thenReturn(
				new EvoPaymentsGatewayPort.EvoSession("SESSION0001BR", "TESTUTEZ", "OK", "df66ca1b01"));

		customUseCase.initiateCheckout(CANDIDATE_ID, null);

		verify(evoPaymentsGateway).initiateCheckoutSession(argThat(order -> order
				.returnUrl().equals(returnWithQuery + "&id=" + CANDIDATE_ID + "&orderId=" + ORDER_ID)));
	}

	@Test
	void missingCandidateIs404() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(CandidateNotFoundException.class)
				.hasMessageContaining("No existe el candidato");
	}

	@Test
	void missingPaymentIs404() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(fichaPaymentPort.findCharge(CHARGE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(CandidateNotFoundException.class)
				.hasMessageContaining("no tiene ficha de pago");
	}

	@Test
	void alreadyPaidIs409() {
		FichaPaymentPort.FichaCharge paid = FichaTestData.paidCharge(CHARGE_ID);
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(fichaPaymentPort.findCharge(CHARGE_ID)).thenReturn(Optional.of(paid));

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(CandidateAlreadyPaidException.class)
				.hasMessageContaining("ya estaba pagada");
	}

	@Test
	void gatewayFailurePropagates() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(evoPaymentsGateway.initiateCheckoutSession(any()))
				.thenThrow(new EvoPaymentGatewayException("El proveedor de pagos (EVO) no pudo iniciar el pago en línea: SERVER_BUSY"));

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(EvoPaymentGatewayException.class)
				.hasMessageContaining("no pudo iniciar el pago en línea");
	}

	// -- the attempt has to exist before Evo is called (§3.6) --

	/**
	 * The ordering this whole slice exists for. The attempt is opened before the
	 * gateway call, not after: once {@code INITIATE_CHECKOUT} runs, Evo may hold an
	 * order that can be captured at any second, and a slot held with no recorded
	 * order id can only be released by guessing.
	 */
	@Test
	void attemptIsOpenedWithTheOrderIdAndAmountBeforeTheGatewayIsCalled() {
		givenPayableCandidate();

		useCase.initiateCheckout(CANDIDATE_ID, null);

		InOrder inOrder = inOrder(checkoutSlotClaimer, evoPaymentsGateway);
		inOrder.verify(checkoutSlotClaimer).claim(CANDIDATE_ID, ORDER_ID, LIVE_QUOTE);
		inOrder.verify(evoPaymentsGateway).initiateCheckoutSession(any());
	}

	/**
	 * A definitive refusal closes the attempt with {@code ORDER_NOT_CREATED} next
	 * to the slot release: nothing was created at the bank, so the history says so
	 * instead of leaving a row that looks like it may still be paying.
	 */
	@Test
	void definitiveGatewayRefusalReleasesTheSlotAndClosesTheAttempt() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(evoPaymentsGateway.initiateCheckoutSession(any())).thenThrow(new EvoPaymentGatewayException(
				"El proveedor de pagos (EVO) no pudo iniciar el pago en línea: INVALID_REQUEST"));

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(EvoPaymentGatewayException.class);

				// Closing the attempt is the release: only an open attempt holds a place.
		verify(checkoutSlotClaimer).closeAttempt(ORDER_ID, CheckoutAttemptCloseReason.ORDER_NOT_CREATED);
	}

	/**
	 * An ambiguous failure keeps both the slot and the open attempt. Evo may have
	 * created the order anyway and captured it later, so releasing here would trade
	 * a stuck career for a possible oversell — the sweep is what settles it, and it
	 * can only do that because the attempt survived.
	 */
	@Test
	void ambiguousGatewayFailureKeepsTheClaimAndLeavesTheAttemptOpen() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(evoPaymentsGateway.initiateCheckoutSession(any())).thenThrow(EvoPaymentGatewayException
				.possiblyCreated("No se pudo contactar al proveedor de pagos (EVO)", null));

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(EvoPaymentGatewayException.class);

		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
	}

	// -- returnPath: the "return me to my own screen" feature, and its guardrails --

	private InitiateFichaPaymentUseCaseImpl useCaseWithAllowlist() {
		return useCaseWithAllowlistAndCancel(CANCEL);
	}

	/**
	 * Every collaborator is required, so the helper has to name all of them. The
	 * fixed clock and the config stub come from {@code setUp}, which is why the
	 * window tests below can rely on "today" meaning {@link #TODAY}.
	 */
	private InitiateFichaPaymentUseCaseImpl useCaseWithAllowlistAndCancel(String cancelBase) {
		return new InitiateFichaPaymentUseCaseImpl(candidateRepository, evoPaymentsGateway,
				orderIdBuilder, "MXN", RETURN, cancelBase, SDK_URL,
				Set.of("/portal/registro/ficha", "/portal/ficha/pago"), programAdmissionConfigQueryPort,
				fichaAmountResolver, fichaPaymentPort, Clock.fixed(NOW_AT_NOON, ZONE), DEADLINE_DAYS,
				checkoutSlotClaimer);
	}

	private void givenPayableCandidate() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(evoPaymentsGateway.initiateCheckoutSession(any())).thenReturn(
				new EvoPaymentsGatewayPort.EvoSession("SESSION0001BR", "TESTUTEZ", "OK", "df66ca1b01"));
	}

	@Test
	void allowlistedReturnPathSwapsThePathAndKeepsOurOrigin() {
		givenPayableCandidate();

		useCaseWithAllowlist().initiateCheckout(CANDIDATE_ID, "/portal/ficha/pago");

		verify(evoPaymentsGateway).initiateCheckoutSession(argThat(order -> order
				.returnUrl().equals(RETURN.replace("/portal/registro/ficha", "/portal/ficha/pago")
						+ "?id=" + CANDIDATE_ID + "&orderId=" + ORDER_ID)));
	}

	/**
	 * The open-redirect guard: a path that is not on the allowlist must be
	 * ignored silently, falling back to the configured return URL.
	 */
	@Test
	void unknownReturnPathFallsBackToTheConfiguredUrl() {
		givenPayableCandidate();

		useCaseWithAllowlist().initiateCheckout(CANDIDATE_ID, "https://evil.example/steal");

		verify(evoPaymentsGateway).initiateCheckoutSession(argThat(order -> order.returnUrl()
				.equals(RETURN + "?id=" + CANDIDATE_ID + "&orderId=" + ORDER_ID)));
	}

	@Test
	void traversalAndProtocolRelativePathsAreNotHonoured() {
		String[] hostile = { "//evil.example/x", "/portal/../admin", "/./etc", "", "  ", "javascript:alert(1)" };

		for (String path : hostile) {
			givenPayableCandidate();
			useCaseWithAllowlist().initiateCheckout(CANDIDATE_ID, path);
		}

		// every attempt still went to the configured URL, never to the caller's
		verify(evoPaymentsGateway, times(hostile.length))
				.initiateCheckoutSession(argThat(order -> order.returnUrl()
						.equals(RETURN + "?id=" + CANDIDATE_ID + "&orderId=" + ORDER_ID)));
	}

	/** A null allowlist (default wiring) must not blow up nor honour anything. */
	@Test
	void withoutAnAllowlistEveryReturnPathIsIgnored() {
		givenPayableCandidate();

		useCase.initiateCheckout(CANDIDATE_ID, "/portal/ficha/pago");

		verify(evoPaymentsGateway).initiateCheckoutSession(argThat(order -> order.returnUrl()
				.equals(RETURN + "?id=" + CANDIDATE_ID + "&orderId=" + ORDER_ID)));
	}

	// -- cancelUrl: cancelling must not dump the payer on a screen the backend
	// would have refused to show them (e.g. the weak folio+CURP lookup landing on
	// the full ficha). --

	@Test
	void allowlistedReturnPathAlsoSwapsTheCancelUrlPath() {
		givenPayableCandidate();
		String cancel = "http://localhost:5173/pagos/cancelados";

		useCaseWithAllowlistAndCancel(cancel).initiateCheckout(CANDIDATE_ID, "/portal/ficha/pago");

		String expected = "http://localhost:5173/portal/ficha/pago" + "?id=" + CANDIDATE_ID
				+ "&orderId=" + ORDER_ID;
		verify(evoPaymentsGateway).initiateCheckoutSession(argThat(order -> expected.equals(order.returnUrl())
				&& expected.equals(order.cancelUrl())));
	}

	@Test
	void unknownReturnPathLeavesTheCancelUrlUntouched() {
		givenPayableCandidate();
		String cancel = "http://localhost:5173/pagos/cancelados";

		useCaseWithAllowlistAndCancel(cancel).initiateCheckout(CANDIDATE_ID, "https://evil.example/steal");

		verify(evoPaymentsGateway).initiateCheckoutSession(argThat(order -> order.cancelUrl()
				.equals(cancel + "?id=" + CANDIDATE_ID + "&orderId=" + ORDER_ID)));
	}

	/**
	 * A deployment that never configured a cancel URL must not end up with an
	 * empty one: it falls back to the (already path-swapped) return URL.
	 */
	@Test
	void withoutAConfiguredCancelUrlItFallsBackToTheResolvedReturnUrl() {
		givenPayableCandidate();

		new InitiateFichaPaymentUseCaseImpl(candidateRepository, evoPaymentsGateway,
				orderIdBuilder, "MXN", RETURN, "", SDK_URL,
				Set.of("/portal/registro/ficha", "/portal/ficha/pago"), programAdmissionConfigQueryPort,
				fichaAmountResolver, fichaPaymentPort, Clock.fixed(NOW_AT_NOON, ZONE), DEADLINE_DAYS, checkoutSlotClaimer).initiateCheckout(CANDIDATE_ID,
						"/portal/ficha/pago");

		String expected = "http://localhost:5173/portal/ficha/pago" + "?id=" + CANDIDATE_ID
				+ "&orderId=" + ORDER_ID;
		verify(evoPaymentsGateway).initiateCheckoutSession(argThat(order -> expected.equals(order.cancelUrl())));
	}

	// ── block 3: the tuition concept's availability window, re-checked at payment ──

	/**
	 * The test the whole block exists for. A concept window that closed after
	 * registration must stop the checkout — and, critically, must stop it
	 * <em>before</em> the gateway is called. Once {@code INITIATE_CHECKOUT} has
	 * run, Evo holds a PENDING order against an id we will never reuse, so
	 * re-opening the period would immediately hit the duplicate-order rejection
	 * documented in §2.3 of the plan. Refusing locally is what keeps a closed
	 * period reversible.
	 */
	@Test
	void aClosedConceptWindowStopsTheCheckoutWithoutCallingEvo() {
		// Deliberately NOT givenPayableCandidate(): that helper stubs the gateway
		// session, and leaving the stub unused is itself the assertion — Mockito's
		// strict mode fails the test if the use case ever reaches Evo.
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		doThrow(new PaymentConceptExpiredException("El pago de la ficha de esta carrera cerró el 20/09/2026."))
				.when(fichaAmountResolver).requirePayableOn(PROGRAM_ID, TODAY);

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(PaymentConceptExpiredException.class)
				.hasMessageContaining("No se encontró pago vigente configurado")
				.hasMessageNotContaining("20/09/2026");

		verify(evoPaymentsGateway, never()).initiateCheckoutSession(any());
		verify(fichaPaymentPort, never()).freeze(any());
	}

	/**
	 * The other half of gate 2: a program with no tuition concept at all is still
	 * "admissions has no payable payment configured", so it takes the same
	 * office-facing message as a concept that is merely outside its window. The
	 * type (and therefore the 409 code) is preserved so the front can keep
	 * branching on it.
	 */
	@Test
	void aMissingConceptIsReportedAsAnAdmissionsConfigurationProblem() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		doThrow(new FichaPaymentConceptNotFoundException("No hay concepto de ficha para el programa."))
				.when(fichaAmountResolver).requirePayableOn(PROGRAM_ID, TODAY);

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(FichaPaymentConceptNotFoundException.class)
				.hasMessageContaining("Comunícate con Servicios escolares");

		verify(evoPaymentsGateway, never()).initiateCheckoutSession(any());
	}

	// ── the ficha's own 10-day deadline (gate 0) and the process' close (gate 1) ──

	/**
	 * Gate 0 and its day-0 convention: a ficha registered on the 10th is payable
	 * through the 20th and refused on the 21st. The concept window is left wide
	 * open, which is the point — this ficha's own clock is what closed, and the
	 * applicant must be told that, not sent to the catalog.
	 */
	@Test
	void aFichaPastItsPrivateDeadlineIsRefused() {
		Candidate stale = candidateRegisteredOn(LocalDate.of(2026, 9, 10));
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(stale));

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(FichaPaymentExpiredException.class)
				.hasMessageContaining("Tu ficha venció");

		// Gate 0 runs first: the concept is never consulted, and no order is burned.
		verify(fichaAmountResolver, never()).requirePayableOn(any(), any());
		verify(evoPaymentsGateway, never()).initiateCheckoutSession(any());
	}

	/** On the deadline itself the ficha is still payable — the window closes at 23:59:59. */
	@Test
	void theFichaIsStillPayableOnItsDeadlineDay() {
		Candidate onLastDay = candidateRegisteredOn(LocalDate.of(2026, 9, 15));
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(onLastDay));
		when(evoPaymentsGateway.initiateCheckoutSession(any())).thenReturn(
				new EvoPaymentsGatewayPort.EvoSession("SESSION0001BR", "TESTUTEZ", "OK", "df66ca1b01"));

		useCase.initiateCheckout(CANDIDATE_ID, null);

		verify(evoPaymentsGateway).initiateCheckoutSession(any());
	}

	/**
	 * Gate 0 read as the whole window: a ficha whose own ten days are still ahead
	 * is refused once her admission process has closed, because the window is the
	 * earlier of the two bounds.
	 *
	 * <p>Reported as {@link FichaPaymentExpiredException}, not as "sales closed".
	 * Both bounds were once separate errors, which told the applicant a sale had
	 * ended while the screen she was standing on still offered her the button.
	 * Which date ran out is not a fact she can act on — her ficha is simply no
	 * longer payable.
	 */
	@Test
	void aPaymentAfterTheAdmissionProcessClosedIsRefusedAsExpired() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(programAdmissionConfigQueryPort.findById(ADMISSION_CONFIG_ID))
				.thenReturn(Optional.of(admissionConfigClosingOn(LocalDate.of(2026, 9, 20))));

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(FichaPaymentExpiredException.class)
				.hasMessageContaining("Tu ficha venció");

		verify(fichaAmountResolver, never()).requirePayableOn(any(), any());
		verify(evoPaymentsGateway, never()).initiateCheckoutSession(any());
	}

	/**
	 * The §10.2 regression. A ficha issued before the process closed stays payable
	 * all through its closing day: the gate compares calendar dates, so the
	 * closing time-of-day is irrelevant. An {@code Instant} check would have
	 * refused this payment — noon is after a midnight close — which is exactly the
	 * bug that killed valid payments.
	 */
	@Test
	void theClosingDayStaysPayableRegardlessOfTimeOfDay() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(programAdmissionConfigQueryPort.findById(ADMISSION_CONFIG_ID))
				.thenReturn(Optional.of(admissionConfigClosingOn(TODAY)));
		when(evoPaymentsGateway.initiateCheckoutSession(any())).thenReturn(
				new EvoPaymentsGatewayPort.EvoSession("SESSION0001BR", "TESTUTEZ", "OK", "df66ca1b01"));

		useCase.initiateCheckout(CANDIDATE_ID, null);

		verify(evoPaymentsGateway).initiateCheckoutSession(any());
	}

	private static ProgramAdmissionConfigQueryPort.AdmissionConfigInfo admissionConfigClosingOn(LocalDate closeDay) {
		return new ProgramAdmissionConfigQueryPort.AdmissionConfigInfo(ADMISSION_CONFIG_ID,
				ProgramAdmissionConfigStatus.OPEN, PROGRAM_ID, "Ingeniería en Software", null, null, null,
				LocalDate.of(2026, 9, 1).atStartOfDay(ZONE).toInstant(), closeDay.atStartOfDay(ZONE).toInstant(), 40);
	}

	@Test
	void theWindowIsCheckedAgainstTheCandidatesOwnProgram() {
		givenPayableCandidate();

		useCase.initiateCheckout(CANDIDATE_ID, null);

		// candidate → admissionConfig → program: getting this chain wrong would
		// check a different career's concept and pass a closed one.
		verify(programAdmissionConfigQueryPort).findById(ADMISSION_CONFIG_ID);
		verify(fichaAmountResolver).requirePayableOn(PROGRAM_ID, TODAY);
	}

	@Test
	void theAmountIsTheOneTheCatalogQuotesAtCheckout() {
		givenPayableCandidate();

		useCase.initiateCheckout(CANDIDATE_ID, null);

		// The live price governs (§1.3): the ficha's charge is frozen at the click,
		// the attempt records that same snapshot (committed before Evo), and that
		// number is what Evo is asked to charge — not the 500.00 the ficha was
		// issued with.
		verify(fichaPaymentPort).freeze(CHARGE_ID);
		verify(checkoutSlotClaimer).claim(CANDIDATE_ID, ORDER_ID, LIVE_QUOTE);
		verify(evoPaymentsGateway).initiateCheckoutSession(argThat(order ->
				LIVE_AMOUNT.compareTo(order.amount()) == 0));
	}

	// ── the live tariff reaches the ficha, not just the gateway ──

	/**
	 * A real claimer, on purpose, because the reprice lives in
	 * {@code CheckoutSlotClaimer#claim} and not in this use case.
	 *
	 	 * <p>{@link #theAmountIsTheOneTheCatalogQuotesAtCheckout()} proves the live
	 * number reaches the claimer, and {@code CheckoutSlotClaimerTest} proves the
	 * claimer writes what it is given. What is pinned here is the thread between
	 * them: the frozen quote is the attempt's snapshot, and the order sent to the
	 * bank carries that same amount — the confirmation books the capture with it.
	 */
	@Test
	void theLiveQuoteIsSnapshottedOnTheAttemptBeforeEvoIsAsked() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionQuotaPort.lockQuota(ADMISSION_CONFIG_ID))
				.thenReturn(new AdmissionQuotaPort.QuotaState(40));
		when(admissionQuotaPort.countOccupiedExcluding(any(), any(), any())).thenReturn(0L);
		when(evoPaymentsGateway.initiateCheckoutSession(any())).thenReturn(
				new EvoPaymentsGatewayPort.EvoSession("SESSION0001BR", "TESTUTEZ", "OK", "df66ca1b01"));

		useCaseWithRealClaimer().initiateCheckout(CANDIDATE_ID, null);

		// The attempt — the row that holds the place — carries the frozen live quote,
		// and that same number is what the bank is asked for.
		ArgumentCaptor<CheckoutAttempt> attempt = ArgumentCaptor.forClass(CheckoutAttempt.class);
		verify(checkoutAttemptRepository).save(attempt.capture());
		assertThat(attempt.getValue().getAmount()).isEqualByComparingTo(LIVE_AMOUNT);
		assertThat(attempt.getValue().getPaymentId()).isEqualTo(LIVE_QUOTE.financePaymentId());
		assertThat(attempt.getValue().getCandidateId()).isEqualTo(CANDIDATE_ID);
		verify(evoPaymentsGateway).initiateCheckoutSession(argThat(order ->
				LIVE_AMOUNT.compareTo(order.amount()) == 0));
	}

	/**
	 * The retry case, with the claimer real: one charge, two attempts, append-only.
	 * Each checkout opens its own attempt under its own {@code orderId}, both open
	 * and both hanging from the same finance charge — that is what lets
	 * reconciliation ask the bank about the order the applicant abandoned.
	 */
	@Test
	void aRetryReusesTheChargeAndAppendsASecondAttempt() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionQuotaPort.lockQuota(ADMISSION_CONFIG_ID))
				.thenReturn(new AdmissionQuotaPort.QuotaState(40));
		when(admissionQuotaPort.countOccupiedExcluding(any(), any(), any())).thenReturn(0L);
		when(evoPaymentsGateway.initiateCheckoutSession(any())).thenReturn(
				new EvoPaymentsGatewayPort.EvoSession("SESSION0001BR", "TESTUTEZ", "OK", "df66ca1b01"));

		InitiateFichaPaymentUseCaseImpl realBuilderUseCase = useCaseWithRealClaimer(
				new OrderIdBuilder("TESTUTEZ", 32));

		String first = realBuilderUseCase.initiateCheckout(CANDIDATE_ID, null).orderId();
		String second = realBuilderUseCase.initiateCheckout(CANDIDATE_ID, null).orderId();

		assertThat(first).isNotEqualTo(second);
		verify(fichaPaymentPort, never()).raiseFichaDebt(any());

		ArgumentCaptor<CheckoutAttempt> attempts = ArgumentCaptor.forClass(CheckoutAttempt.class);
		verify(checkoutAttemptRepository, times(2)).save(attempts.capture());
		List<CheckoutAttempt> history = attempts.getAllValues();
		assertThat(history).extracting(CheckoutAttempt::getOrderId).containsExactly(first, second);
		assertThat(history).allSatisfy(attempt -> {
			assertThat(attempt.getPaymentId()).isEqualTo(LIVE_QUOTE.financePaymentId());
			assertThat(attempt.isOpen()).isTrue();
			assertThat(attempt.getCloseReason()).isEqualTo(CheckoutAttemptCloseReason.STARTED);
			assertThat(attempt.getClosedAt()).isNull();
		});
	}

	/**
	 * The real claimer, wired to the same mocks this class uses for the
	 * orchestration tests.
	 */
	private InitiateFichaPaymentUseCaseImpl useCaseWithRealClaimer() {
		return useCaseWithRealClaimer(orderIdBuilder);
	}

	private InitiateFichaPaymentUseCaseImpl useCaseWithRealClaimer(OrderIdBuilder orderIdBuilder) {
		CheckoutSlotClaimer realClaimer = new CheckoutSlotClaimer(admissionQuotaPort, candidateRepository,
				checkoutAttemptRepository, fichaPaymentPort, Clock.fixed(NOW_AT_NOON, ZONE), 15);
		return new InitiateFichaPaymentUseCaseImpl(candidateRepository, evoPaymentsGateway,
				orderIdBuilder, "MXN", RETURN, CANCEL, SDK_URL, Set.of(), programAdmissionConfigQueryPort,
				fichaAmountResolver, fichaPaymentPort, Clock.fixed(NOW_AT_NOON, ZONE), DEADLINE_DAYS, realClaimer);
	}

	@Test
	void aFichaWhoseAdmissionConfigVanishedIsRejectedRatherThanPaid() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(programAdmissionConfigQueryPort.findById(ADMISSION_CONFIG_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(ProgramAdmissionConfigNotFoundException.class);

		verify(evoPaymentsGateway, never()).initiateCheckoutSession(any());
	}

	@Test
	void anAlreadyPaidFichaIsRejectedBeforeTheWindowIsEvenConsulted() {
		FichaPaymentPort.FichaCharge paid = FichaTestData.paidCharge(CHARGE_ID);
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(fichaPaymentPort.findCharge(CHARGE_ID)).thenReturn(Optional.of(paid));

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(CandidateAlreadyPaidException.class);

		// "You already paid" is the more useful answer than "the period closed",
		// so the window is not even looked at.
		verify(fichaAmountResolver, never()).requirePayableOn(any(), any());
		verify(evoPaymentsGateway, never()).initiateCheckoutSession(any());
	}

	// ── block 4: the quota slot, claimed around the gateway call ──

	/**
	 * The ordering the whole quota rule rests on. The slot has to be taken
	 * <em>before</em> Evo is asked for money, because this is the last moment a
	 * refusal is free; and the session has to be persisted only <em>after</em>,
	 * because recording an order id we never got would leave the confirmation
	 * looking up an order that does not exist.
	 */
	@Test
	void theSlotIsClaimedBeforeEvoAndTheSessionIsPersistedAfter() {
		givenPayableCandidate();

		useCase.initiateCheckout(CANDIDATE_ID, null);

		InOrder inOrder = inOrder(checkoutSlotClaimer, evoPaymentsGateway);
		inOrder.verify(checkoutSlotClaimer).claim(CANDIDATE_ID, ORDER_ID, LIVE_QUOTE);
		inOrder.verify(evoPaymentsGateway).initiateCheckoutSession(any());
		inOrder.verify(checkoutSlotClaimer).persistCheckoutSession(ORDER_ID, "SESSION0001BR");
	}

	/**
	 * A career that is already full is refused without touching the gateway. The
	 * refusal costs the applicant nothing because no order was ever created.
	 */
	@Test
	void aFullQuotaIsRefusedBeforeEvoIsCalled() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		doThrow(new ProgramAdmissionConfigCapacityReachedException("El cupo de esta carrera se agotó."))
				.when(checkoutSlotClaimer).claim(CANDIDATE_ID, ORDER_ID, LIVE_QUOTE);

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(ProgramAdmissionConfigCapacityReachedException.class)
				.hasMessage("El cupo de esta carrera se agotó.");

		verify(evoPaymentsGateway, never()).initiateCheckoutSession(any());
		verify(checkoutSlotClaimer, never()).persistCheckoutSession(any(), any());
	}

	/**
	 * Evo refused outright and no order exists, so the slot goes straight back.
	 * Without this the career would sit one place short until its payment window
	 * closed, for an order that was never created.
	 */
	@Test
	void aDefinitiveGatewayRefusalHandsTheSlotBack() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(evoPaymentsGateway.initiateCheckoutSession(any()))
				.thenThrow(new EvoPaymentGatewayException("EVO rechazó la operación: ORDER_ALREADY_EXISTS"));

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(EvoPaymentGatewayException.class);

		verify(checkoutSlotClaimer).closeAttempt(any(), eq(CheckoutAttemptCloseReason.ORDER_NOT_CREATED));
		verify(checkoutSlotClaimer, never()).persistCheckoutSession(any(), any());
	}

	/**
	 * The dangerous one. A timeout or a dropped response does not prove Evo has no
	 * order — the request may have been received and captured moments later.
	 * Releasing here would hand the last slot to a second applicant while the first
	 * payment is still live, which is the overshoot this whole block exists to
	 * prevent. So an ambiguous failure keeps the claim, and reconciliation settles
	 * it later.
	 */
	@Test
	void anAmbiguousGatewayFailureKeepsTheClaim() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(evoPaymentsGateway.initiateCheckoutSession(any())).thenThrow(
				EvoPaymentGatewayException.possiblyCreated("No se pudo contactar al proveedor de pagos (EVO): timeout",
						null));

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(EvoPaymentGatewayException.class);

		verify(checkoutSlotClaimer, never()).closeAttempt(any(), any());
	}

	// ── discounts: priced at the click, 100 % closes without EVO ──

	/**
	 * A discount granted after registration reaches the applicant: the checkout
	 * freezes the charge's live quote (tariff − discount) and that is the amount
	 * EVO is asked for.
	 */
	@Test
	void aDiscountInForceAtTheClickIsWhatEvoCharges() {
		givenPayableCandidate();
		FichaPaymentPort.FichaQuote discounted = new FichaPaymentPort.FichaQuote(CHARGE_ID, "FICHA-ING",
				"Inscripción", UUID.randomUUID(), LIVE_AMOUNT, new BigDecimal("137.50"), new BigDecimal("412.50"),
				UUID.randomUUID(), false, false, null);
		when(fichaPaymentPort.freeze(CHARGE_ID)).thenReturn(discounted);

		useCase.initiateCheckout(CANDIDATE_ID, null);

		verify(checkoutSlotClaimer).claim(CANDIDATE_ID, ORDER_ID, discounted);
		verify(evoPaymentsGateway).initiateCheckoutSession(argThat(order ->
				new BigDecimal("412.50").compareTo(order.amount()) == 0));
	}

	/** A 100 % discount settles the ficha as exempt and never opens an EVO session. */
	@Test
	void aFullDiscountSettlesTheFichaWithoutTheGateway() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		FichaPaymentPort.FichaQuote exempt = new FichaPaymentPort.FichaQuote(CHARGE_ID, "FICHA-ING", "Inscripción",
				null, LIVE_AMOUNT, LIVE_AMOUNT, new BigDecimal("0.00"), UUID.randomUUID(), true, false, null);
		when(fichaPaymentPort.freeze(CHARGE_ID)).thenReturn(exempt);

		var result = useCase.initiateCheckout(CANDIDATE_ID, null);

		assertThat(result.exempt()).isTrue();
		verify(checkoutSlotClaimer).settleExempt(CANDIDATE_ID, exempt);
		verify(evoPaymentsGateway, never()).initiateCheckoutSession(any());
		verify(checkoutSlotClaimer, never()).claim(any(), any(), any());
	}

	// ── Diferencia por cambio de carrera ────────────────────────────────────

	private static final UUID DIFFERENCE_ID = UUID.randomUUID();

	private static Candidate candidateOwingADifference() {
		Candidate candidate = candidate();
		candidate.markPaid();
		candidate.awaitProgramChangePayment(DIFFERENCE_ID);
		return candidate;
	}

	private static FichaPaymentPort.ProgramChangeCharge difference(Instant dueAt) {
		return new FichaPaymentPort.ProgramChangeCharge(DIFFERENCE_ID, FichaTestData.REFERENCE + "-C1",
				new BigDecimal("1750.00"), new BigDecimal("1420.00"), new BigDecimal("330.00"), dueAt, false, false,
				null, null);
	}

	@Test
	void aPendingDifferenceIsChargedForWhatIsLeftWithoutRequoting() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidateOwingADifference()));
		when(fichaPaymentPort.findProgramChangeCharge(DIFFERENCE_ID))
				.thenReturn(Optional.of(difference(NOW_AT_NOON.plusSeconds(86_400))));
		when(evoPaymentsGateway.initiateCheckoutSession(any())).thenReturn(
				new EvoPaymentsGatewayPort.EvoSession("SESSION0002", "TESTUTEZ", "SUCCESS0002", "v1"));

		InitiateCheckoutResult result = useCase.initiateCheckout(CANDIDATE_ID, null);

		assertThat(result.orderId()).isEqualTo(ORDER_ID);
		verify(checkoutSlotClaimer).claimProgramChange(CANDIDATE_ID, ORDER_ID, DIFFERENCE_ID,
				new BigDecimal("330.00"));
		verify(evoPaymentsGateway).initiateCheckoutSession(argThat(order -> new BigDecimal("330.00")
				.compareTo(order.amount()) == 0 && (FichaTestData.REFERENCE + "-C1").equals(order.reference())
				&& "Diferencia cambio de carrera ADM-2026-000001".equals(order.description())));
		verify(fichaPaymentPort, never()).freeze(any());
		verify(checkoutSlotClaimer, never()).claim(any(), any(), any());
	}

	@Test
	void aDifferencePastItsDeadlineIsNotCharged() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidateOwingADifference()));
		when(fichaPaymentPort.findProgramChangeCharge(DIFFERENCE_ID))
				.thenReturn(Optional.of(difference(NOW_AT_NOON.minusSeconds(60))));

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(FichaPaymentExpiredException.class);
		verify(evoPaymentsGateway, never()).initiateCheckoutSession(any());
	}
}
