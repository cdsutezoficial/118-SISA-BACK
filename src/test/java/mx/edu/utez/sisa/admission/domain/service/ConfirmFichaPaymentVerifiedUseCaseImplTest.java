package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase.ConfirmPaymentResult;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.InvalidPaymentVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConfirmFichaPaymentVerifiedUseCaseImpl} (Fase 5): the
 * online confirm demands EVO {@code SUCCESS} + matching amount before delegating
 * to the local confirm; failures (missmatch, FAILURE verdict, wrong/missing
 * orderId, no session) are {@link InvalidPaymentVerificationException} (→ 400);
 * the legacy window path and idempotent 409 are preserved.
 */
@ExtendWith(MockitoExtension.class)
class ConfirmFichaPaymentVerifiedUseCaseImplTest {

	private static final UUID CANDIDATE_ID = UUID.randomUUID();

	private static final String ORDER_ID = "TESTUTEZ-ADM-2026-000001";

	private final ConfirmPaymentResult paid = new ConfirmPaymentResult(CANDIDATE_ID, "ADM-2026-000001",
			CandidateStatus.PAID, "REF-2026-000001", new BigDecimal("500.00"),
			Instant.parse("2026-09-24T12:00:00Z"), "REC-20260924-000001");

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private AdmissionPaymentRepository admissionPaymentRepository;

	@Mock
	private EvoPaymentsGatewayPort evoPaymentsGateway;

	@Mock
	private ConfirmAdmissionPaymentUseCase confirmAdmissionPaymentUseCase;

	private ConfirmFichaPaymentVerifiedUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ConfirmFichaPaymentVerifiedUseCaseImpl(candidateRepository, admissionPaymentRepository,
				evoPaymentsGateway, confirmAdmissionPaymentUseCase);
	}

	private static Candidate candidate() {
		return new Candidate(UUID.randomUUID(), UUID.randomUUID(), "ADM-2026-000001", true, true, null);
	}

	private static AdmissionPayment payment() {
		return new AdmissionPayment(CANDIDATE_ID, AdmissionPaymentConcept.ADMISSION_FICHA,
				new BigDecimal("500.00"), "REF-2026-000001", LocalDate.now().plusDays(10));
	}

	@Test
	void onlineConfirmDelegatesLocalConfirmOnSuccess() {
		AdmissionPayment payment = payment();
		payment.registerCheckout(ORDER_ID, "SESSION0001BR");
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment));
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "SUCCESS", new BigDecimal("500.00")));
		when(confirmAdmissionPaymentUseCase.confirm(CANDIDATE_ID)).thenReturn(paid);

		ConfirmPaymentResult result = useCase.confirm(CANDIDATE_ID, ORDER_ID);

		assertThat(result.receiptNumber()).isEqualTo("REC-20260924-000001");
		verify(evoPaymentsGateway).retrieveOrder(ORDER_ID);
		verify(confirmAdmissionPaymentUseCase).confirm(CANDIDATE_ID);
	}

	@Test
	void onlineConfirmRejectsWhenClientOmitsOrderId() {
		// orderId is MANDATORY: without it there is nothing to cross-check the
		// return against, and the previous fallback marked the ficha PAID on a
		// bare POST. Now it must be rejected before EVO is ever consulted.
		AdmissionPayment payment = payment();
		payment.registerCheckout(ORDER_ID, "SESSION0001BR");
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment));

		assertThatThrownBy(() -> useCase.confirm(CANDIDATE_ID, null))
				.isInstanceOf(InvalidPaymentVerificationException.class)
				.hasMessageContaining("identificador del pedido es obligatorio");
		verify(evoPaymentsGateway, never()).retrieveOrder(any());
		verify(confirmAdmissionPaymentUseCase, never()).confirm(any());
	}

	@Test
	void onlineConfirmRejectsMismatchedOrderId() {
		AdmissionPayment payment = payment();
		payment.registerCheckout(ORDER_ID, "SESSION0001BR");
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment));

		assertThatThrownBy(() -> useCase.confirm(CANDIDATE_ID, "TESTUTEZ-ADM-2026-000002"))
				.isInstanceOf(InvalidPaymentVerificationException.class)
				.hasMessageContaining("no corresponde a esta ficha");
		verify(evoPaymentsGateway, never()).retrieveOrder(any());
	}

	@Test
	void onlineConfirmRejectsWhenNoSessionInitiated() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment()));

		assertThatThrownBy(() -> useCase.confirm(CANDIDATE_ID, ORDER_ID))
				.isInstanceOf(InvalidPaymentVerificationException.class)
				.hasMessageContaining("no tiene un pago en línea iniciado");
		verify(evoPaymentsGateway, never()).retrieveOrder(any());
	}

	@Test
	void rejectsFailureVerdictFromGateway() {
		AdmissionPayment payment = payment();
		payment.registerCheckout(ORDER_ID, "SESSION0001BR");
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment));
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "FAILURE", null));

		assertThatThrownBy(() -> useCase.confirm(CANDIDATE_ID, ORDER_ID))
				.isInstanceOf(InvalidPaymentVerificationException.class)
				.hasMessageContaining("no fue confirmado por el procesador");
		verify(confirmAdmissionPaymentUseCase, never()).confirm(any());
	}

	@Test
	void rejectsMismatchedAmountFromGateway() {
		AdmissionPayment payment = payment();
		payment.registerCheckout(ORDER_ID, "SESSION0001BR");
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment));
		when(evoPaymentsGateway.retrieveOrder(ORDER_ID)).thenReturn(
				new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, "SUCCESS", new BigDecimal("499.99")));

		assertThatThrownBy(() -> useCase.confirm(CANDIDATE_ID, ORDER_ID))
				.isInstanceOf(InvalidPaymentVerificationException.class)
				.hasMessageContaining("monto del pago");
		verify(confirmAdmissionPaymentUseCase, never()).confirm(any());
	}

	@Test
	void legacyWindowConfirmIsRejectedWithNoOrderId() {
		// The window-payment path was removed: a pending ficha with no online
		// session and no orderId can no longer be confirmed. Previously this
		// delegated straight to the local confirm and returned PAID.
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(payment()));

		assertThatThrownBy(() -> useCase.confirm(CANDIDATE_ID, null))
				.isInstanceOf(InvalidPaymentVerificationException.class)
				.hasMessageContaining("identificador del pedido es obligatorio");
		verify(evoPaymentsGateway, never()).retrieveOrder(any());
		verify(confirmAdmissionPaymentUseCase, never()).confirm(any());
	}

	@Test
	void alreadyPaidIs409WithoutHittingTheGateway() {
		AdmissionPayment paidPayment = payment();
		paidPayment.markPaid("REC-20260924-000001");
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.of(paidPayment));

		assertThatThrownBy(() -> useCase.confirm(CANDIDATE_ID, ORDER_ID))
				.isInstanceOf(CandidateAlreadyPaidException.class)
				.hasMessageContaining("ya estaba pagada");
		verify(evoPaymentsGateway, never()).retrieveOrder(any());
		verify(confirmAdmissionPaymentUseCase, never()).confirm(any());
	}

	@Test
	void missingCandidateIs404() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.confirm(CANDIDATE_ID, ORDER_ID))
				.isInstanceOf(CandidateNotFoundException.class);
	}

	@Test
	void missingPaymentIs404() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate()));
		when(admissionPaymentRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.confirm(CANDIDATE_ID, ORDER_ID))
				.isInstanceOf(CandidateNotFoundException.class);
	}
}