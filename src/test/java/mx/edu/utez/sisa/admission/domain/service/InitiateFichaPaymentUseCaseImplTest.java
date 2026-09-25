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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InitiateFichaPaymentUseCaseImpl} (Fase 4): a
 * successful checkout builds the EVO order (order.id = prefix+folio, amount
 * from the payment concept, description with the folio), persists
 * {@code order_id} + {@code checkout_session_id} on the payment, returns the
 * derived {@code checkoutUrl}; 404/409 guard cases; gateway failure propagates
 * as {@link EvoPaymentGatewayException} (→ 502 at the web layer, handled in
 * {@code CandidateControllerTest}).
 */
@ExtendWith(MockitoExtension.class)
class InitiateFichaPaymentUseCaseImplTest {

	private static final String RETURN = "http://localhost:5173/portal/registro/ficha";
	private static final String CANCEL = "http://localhost:5173/portal/registro/ficha";
	private static final String PAGE_BASE = "https://evopaymentsmexico.gateway.mastercard.com";
	private static final String PAGE_VERSION = "72";

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
				evoPaymentsGateway, orderIdBuilder, "MXN", RETURN, CANCEL, PAGE_BASE, PAGE_VERSION);
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

		InitiateCheckoutResult result = useCase.initiateCheckout(CANDIDATE_ID);

		assertThat(result.candidateId()).isEqualTo(CANDIDATE_ID);
		assertThat(result.orderId()).isEqualTo("TESTUTEZ-ADM-2026-000001");
		assertThat(result.sessionId()).isEqualTo("SESSION0001BR");
		assertThat(result.version()).isEqualTo("df66ca1b01");
		assertThat(result.merchant()).isEqualTo("TESTUTEZ");
		assertThat(result.successIndicator()).isEqualTo("AAAA/BRAVO/SUCCESS0001");
		assertThat(result.checkoutUrl()).isEqualTo(PAGE_BASE + "/api/page/version/" + PAGE_VERSION + "/pay");

		verify(evoPaymentsGateway).initiateCheckoutSession(argThat(order -> "TESTUTEZ-ADM-2026-000001".equals(order.id())
				&& "REF-2026-000001".equals(order.reference())
				&& new BigDecimal("500.00").compareTo(order.amount()) == 0 && "MXN".equals(order.currency())
				&& "Ficha de Admisión ADM-2026-000001".equals(order.description())
				&& RETURN.equals(order.returnUrl()) && CANCEL.equals(order.cancelUrl())));
		ArgumentCaptor<AdmissionPayment> saved = ArgumentCaptor.forClass(AdmissionPayment.class);
		verify(admissionPaymentRepository).save(saved.capture());
		assertThat(saved.getValue().getOrderId()).isEqualTo("TESTUTEZ-ADM-2026-000001");
		assertThat(saved.getValue().getCheckoutSessionId()).isEqualTo("SESSION0001BR");
	}

	@Test
	void pageUrlUsesTheConfiguredApiVersionNotTheSessionToken() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment()));
		when(evoPaymentsGateway.initiateCheckoutSession(any())).thenReturn(
				new EvoPaymentsGatewayPort.EvoSession("SESSION0001BR", "TESTUTEZ", "OK", null));

		InitiateCheckoutResult result = useCase.initiateCheckout(CANDIDATE_ID);

		assertThat(result.checkoutUrl()).isEqualTo(PAGE_BASE + "/api/page/version/" + PAGE_VERSION + "/pay");
	}

	@Test
	void missingCandidateIs404() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID))
				.isInstanceOf(CandidateNotFoundException.class)
				.hasMessageContaining("No existe el candidato");
	}

	@Test
	void missingPaymentIs404() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID))
				.isInstanceOf(CandidateNotFoundException.class)
				.hasMessageContaining("no tiene ficha de pago");
	}

	@Test
	void alreadyPaidIs409() {
		AdmissionPayment paid = payment();
		paid.markPaid("REC-20260924-000001");
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(paid));

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID))
				.isInstanceOf(CandidateAlreadyPaidException.class)
				.hasMessageContaining("ya estaba pagada");
	}

	@Test
	void gatewayFailurePropagates() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment()));
		when(evoPaymentsGateway.initiateCheckoutSession(any()))
				.thenThrow(new EvoPaymentGatewayException("El proveedor de pagos (EVO) no pudo iniciar el pago en línea: SERVER_BUSY"));

		assertThatThrownBy(() -> useCase.initiateCheckout(CANDIDATE_ID))
				.isInstanceOf(EvoPaymentGatewayException.class)
				.hasMessageContaining("no pudo iniciar el pago en línea");
	}
}