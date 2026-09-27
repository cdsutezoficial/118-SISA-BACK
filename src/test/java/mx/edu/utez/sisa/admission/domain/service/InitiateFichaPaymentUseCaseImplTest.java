package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.InitiateFichaPaymentUseCase.InitiateCheckoutResult;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;
import mx.edu.utez.sisa.admission.shared.exception.PaymentConceptExpiredException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
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

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private AdmissionPaymentRepository admissionPaymentRepository;

	@Mock
	private EvoPaymentsGatewayPort evoPaymentsGateway;

	@Mock
	private OrderIdBuilder orderIdBuilder;

	@Mock
	private ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	@Mock
	private FichaAmountResolver fichaAmountResolver;

	private InitiateFichaPaymentUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		// lenient: the 404/409 guard cases throw before the builder is ever consulted
		lenient().when(orderIdBuilder.build(any())).thenReturn(ORDER_ID);
		// lenient: only the paths that get past the PAID check resolve the config
		lenient().when(programAdmissionConfigQueryPort.findById(ADMISSION_CONFIG_ID))
				.thenReturn(Optional.of(new ProgramAdmissionConfigQueryPort.AdmissionConfigInfo(ADMISSION_CONFIG_ID,
						ProgramAdmissionConfigStatus.OPEN, PROGRAM_ID, "Ingeniería en Software", null, null,
						LocalDate.of(2026, 9, 1).atStartOfDay(ZONE).toInstant(),
						LocalDate.of(2026, 9, 30).atStartOfDay(ZONE).toInstant(), 40)));
		useCase = new InitiateFichaPaymentUseCaseImpl(candidateRepository, admissionPaymentRepository,
				evoPaymentsGateway, orderIdBuilder, "MXN", RETURN, CANCEL, SDK_URL, Set.of(),
				programAdmissionConfigQueryPort, fichaAmountResolver, Clock.fixed(NOW_AT_NOON, ZONE));
	}

	private static Candidate candidate() {
		return new Candidate(UUID.randomUUID(), ADMISSION_CONFIG_ID, "ADM-2026-000001", true, true, null);
	}

	private static AdmissionPayment payment() {
		return new AdmissionPayment(CANDIDATE_ID, AdmissionPaymentConcept.ADMISSION_FICHA,
				new BigDecimal("500.00"), "REF-2026-000001", LocalDate.now().plusDays(10));
	}

	@Test
	void successfulCheckoutPersistsSessionAndReturnsCheckoutUrl() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment()));
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
				&& "REF-2026-000001".equals(order.reference())
				&& new BigDecimal("500.00").compareTo(order.amount()) == 0 && "MXN".equals(order.currency())
				&& "Ficha de Admisión ADM-2026-000001".equals(order.description())
				&& expectedReturn.equals(order.returnUrl()) && expectedReturn.equals(order.cancelUrl())));
		ArgumentCaptor<AdmissionPayment> saved = ArgumentCaptor.forClass(AdmissionPayment.class);
		verify(admissionPaymentRepository).save(saved.capture());
		assertThat(saved.getValue().getOrderId()).isEqualTo(ORDER_ID);
		assertThat(saved.getValue().getCheckoutSessionId()).isEqualTo("SESSION0001BR");
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
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment()));
		when(evoPaymentsGateway.initiateCheckoutSession(any())).thenReturn(
				new EvoPaymentsGatewayPort.EvoSession("SESSION0001BR", "TESTUTEZ", "OK", "df66ca1b01"));
		InitiateFichaPaymentUseCaseImpl realBuilderUseCase = new InitiateFichaPaymentUseCaseImpl(candidateRepository,
				admissionPaymentRepository, evoPaymentsGateway, new OrderIdBuilder("TESTUTEZ", 32), "MXN", RETURN,
				CANCEL, SDK_URL, Set.of(), programAdmissionConfigQueryPort, fichaAmountResolver,
				Clock.fixed(NOW_AT_NOON, ZONE));

		String first = realBuilderUseCase.initiateCheckout(CANDIDATE_ID, null).orderId();
		String second = realBuilderUseCase.initiateCheckout(CANDIDATE_ID, null).orderId();

		assertThat(first).isNotEqualTo(second);
		assertThat(first).startsWith("TESTUTEZ-ADM-2026-000001-");
		assertThat(second).startsWith("TESTUTEZ-ADM-2026-000001-");

		ArgumentCaptor<AdmissionPayment> saved = ArgumentCaptor.forClass(AdmissionPayment.class);
		verify(admissionPaymentRepository, times(2)).save(saved.capture());
		assertThat(saved.getAllValues().get(1).getOrderId()).isEqualTo(second);
	}

	@Test
	void appendsCandidateAndOrderToExistingReturnUrlQuery() {
		String returnWithQuery = RETURN + "?origen=checkout";
		InitiateFichaPaymentUseCaseImpl customUseCase = new InitiateFichaPaymentUseCaseImpl(candidateRepository,
				admissionPaymentRepository, evoPaymentsGateway, orderIdBuilder, "MXN", returnWithQuery, CANCEL,
				SDK_URL, Set.of(), programAdmissionConfigQueryPort, fichaAmountResolver, Clock.fixed(NOW_AT_NOON, ZONE));
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment()));
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
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(CandidateNotFoundException.class)
				.hasMessageContaining("no tiene ficha de pago");
	}

	@Test
	void alreadyPaidIs409() {
		AdmissionPayment paid = payment();
		paid.markPaid("REC-20260924-000001");
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(paid));

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(CandidateAlreadyPaidException.class)
				.hasMessageContaining("ya estaba pagada");
	}

	@Test
	void gatewayFailurePropagates() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment()));
		when(evoPaymentsGateway.initiateCheckoutSession(any()))
				.thenThrow(new EvoPaymentGatewayException("El proveedor de pagos (EVO) no pudo iniciar el pago en línea: SERVER_BUSY"));

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(EvoPaymentGatewayException.class)
				.hasMessageContaining("no pudo iniciar el pago en línea");
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
		return new InitiateFichaPaymentUseCaseImpl(candidateRepository, admissionPaymentRepository, evoPaymentsGateway,
				orderIdBuilder, "MXN", RETURN, cancelBase, SDK_URL,
				Set.of("/portal/registro/ficha", "/portal/ficha/pago"), programAdmissionConfigQueryPort,
				fichaAmountResolver, Clock.fixed(NOW_AT_NOON, ZONE));
	}

	private void givenPayableCandidate() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment()));
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

		new InitiateFichaPaymentUseCaseImpl(candidateRepository, admissionPaymentRepository, evoPaymentsGateway,
				orderIdBuilder, "MXN", RETURN, "", SDK_URL,
				Set.of("/portal/registro/ficha", "/portal/ficha/pago"), programAdmissionConfigQueryPort,
				fichaAmountResolver, Clock.fixed(NOW_AT_NOON, ZONE)).initiateCheckout(CANDIDATE_ID,
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
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment()));
		doThrow(new PaymentConceptExpiredException("El pago de la ficha de esta carrera cerró el 20/09/2026."))
				.when(fichaAmountResolver).requirePayableOn(PROGRAM_ID, TODAY);

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(PaymentConceptExpiredException.class).hasMessageContaining("20/09/2026");

		verify(evoPaymentsGateway, never()).initiateCheckoutSession(any());
		verify(admissionPaymentRepository, never()).save(any());
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
	void theAmountIsTheOneFrozenAtRegistration() {
		givenPayableCandidate();

		useCase.initiateCheckout(CANDIDATE_ID, null);

		// The catalog cost is deliberately NOT consulted: admission_payment.amount
		// was set when the ficha was issued, and a price edit between issuing and
		// paying must not change what the applicant owes. requirePayableOn asks
		// the resolver about the window and nothing else; resolve() — the only
		// method that can produce a number — is never called.
		verify(fichaAmountResolver, never()).resolve(any(), any());
		verify(evoPaymentsGateway).initiateCheckoutSession(argThat(order -> order.amount()
				.compareTo(new BigDecimal("500.00")) == 0));
	}

	@Test
	void aFichaWhoseAdmissionConfigVanishedIsRejectedRatherThanPaid() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment()));
		when(programAdmissionConfigQueryPort.findById(ADMISSION_CONFIG_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(ProgramAdmissionConfigNotFoundException.class);

		verify(evoPaymentsGateway, never()).initiateCheckoutSession(any());
	}

	@Test
	void anAlreadyPaidFichaIsRejectedBeforeTheWindowIsEvenConsulted() {
		AdmissionPayment paid = payment();
		paid.markPaid("REC-1");
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(paid));

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID, null))
				.isInstanceOf(CandidateAlreadyPaidException.class);

		// "You already paid" is the more useful answer than "the period closed",
		// so the window is not even looked at.
		verify(fichaAmountResolver, never()).requirePayableOn(any(), any());
		verify(evoPaymentsGateway, never()).initiateCheckoutSession(any());
	}
}