package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.InitiateFichaPaymentUseCase.InitiateCheckoutResult;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InitiateFichaPaymentUseCaseImpl} (Fase 4): a
 * successful checkout builds the EVO order (order.id = prefix+folio, amount
 * from the payment concept, description with the folio), persists
 * {@code order_id} + {@code checkout_session_id} on the payment, returns the
 * Checkout SDK URL and appends the candidate/order to the return URLs;
 * 404/409 guard cases; gateway failure propagates
 * as {@link EvoPaymentGatewayException} (→ 502 at the web layer, handled in
 * {@code CandidateControllerTest}).
 */
@ExtendWith(MockitoExtension.class)
class InitiateFichaPaymentUseCaseImplTest {

	private static final String RETURN = "http://localhost:5173/portal/registro/ficha";
	private static final String CANCEL = "http://localhost:5173/portal/registro/ficha";
	private static final String SDK_URL = "https://evopaymentsmexico.gateway.mastercard.com/static/checkout/checkout.min.js";
	private static final String ORDER_ID = "TESTUTEZ-ADM-2026-000001";

	private static final UUID CANDIDATE_ID = UUID.randomUUID();

	private final OrderIdBuilder orderIdBuilder = new OrderIdBuilder("TESTUTEZ", 32);

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private AdmissionPaymentRepository admissionPaymentRepository;

	@Mock
	private EvoPaymentsGatewayPort evoPaymentsGateway;

	private InitiateFichaPaymentUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new InitiateFichaPaymentUseCaseImpl(candidateRepository, admissionPaymentRepository,
				evoPaymentsGateway, orderIdBuilder, "MXN", RETURN, CANCEL, SDK_URL);
	}

	private static Candidate candidate() {
		return new Candidate(UUID.randomUUID(), UUID.randomUUID(), "ADM-2026-000001", true, true, null);
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

		String expectedReturn = RETURN + "?id=" + CANDIDATE_ID + "&orderId=" + ORDER_ID;
		verify(evoPaymentsGateway).initiateCheckoutSession(argThat(order -> ORDER_ID.equals(order.id())
				&& "REF-2026-000001".equals(order.reference())
				&& new BigDecimal("500.00").compareTo(order.amount()) == 0 && "MXN".equals(order.currency())
				&& "Ficha de Admisión ADM-2026-000001".equals(order.description())
				&& expectedReturn.equals(order.returnUrl()) && expectedReturn.equals(order.cancelUrl())));
		ArgumentCaptor<AdmissionPayment> saved = ArgumentCaptor.forClass(AdmissionPayment.class);
		verify(admissionPaymentRepository).save(saved.capture());
		assertThat(saved.getValue().getOrderId()).isEqualTo("TESTUTEZ-ADM-2026-000001");
		assertThat(saved.getValue().getCheckoutSessionId()).isEqualTo("SESSION0001BR");
	}

	@Test
	void appendsCandidateAndOrderToExistingReturnUrlQuery() {
		String returnWithQuery = RETURN + "?origen=checkout";
		InitiateFichaPaymentUseCaseImpl customUseCase = new InitiateFichaPaymentUseCaseImpl(candidateRepository,
				admissionPaymentRepository, evoPaymentsGateway, orderIdBuilder, "MXN", returnWithQuery, CANCEL,
				SDK_URL);
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
		return new InitiateFichaPaymentUseCaseImpl(candidateRepository, admissionPaymentRepository,
				evoPaymentsGateway, orderIdBuilder, "MXN", RETURN, CANCEL, SDK_URL,
				Set.of("/portal/registro/ficha", "/portal/ficha/pago"));
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

	private InitiateFichaPaymentUseCaseImpl useCaseWithAllowlistAndCancel(String cancelBase) {
		return new InitiateFichaPaymentUseCaseImpl(candidateRepository, admissionPaymentRepository,
				evoPaymentsGateway, orderIdBuilder, "MXN", RETURN, cancelBase, SDK_URL,
				Set.of("/portal/registro/ficha", "/portal/ficha/pago"));
	}

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
				Set.of("/portal/registro/ficha", "/portal/ficha/pago")).initiateCheckout(CANDIDATE_ID,
						"/portal/ficha/pago");

		String expected = "http://localhost:5173/portal/ficha/pago" + "?id=" + CANDIDATE_ID
				+ "&orderId=" + ORDER_ID;
		verify(evoPaymentsGateway).initiateCheckoutSession(argThat(order -> expected.equals(order.cancelUrl())));
	}
}