package mx.edu.utez.sisa.admission.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase.ConfirmPaymentResult;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase.FichaData;

/** F3: what each event mails, and that a ficha without email sends nothing. */
@ExtendWith(MockitoExtension.class)
class CandidateMailListenerTest {

	private static final UUID CANDIDATE_ID = UUID.randomUUID();

	@Mock
	private GetCandidateFichaUseCase getCandidateFichaUseCase;

	@Mock
	private CandidateFichaMailService mailService;

	private CandidateMailListener listener;

	@BeforeEach
	void setUp() {
		listener = new CandidateMailListener(getCandidateFichaUseCase, mailService, "https://sisa.utez.edu.mx/SGA/");
	}

	private void fichaWithEmail(String email) {
		FichaData ficha = mock(FichaData.class);
		when(ficha.email()).thenReturn(email);
		org.mockito.Mockito.lenient().when(ficha.folio()).thenReturn("ADM-2026-000001");
		org.mockito.Mockito.lenient().when(ficha.firstName()).thenReturn("Ana");
		when(getCandidateFichaUseCase.get(CANDIDATE_ID)).thenReturn(ficha);
	}

	@Test
	void aDifferencePaymentIsMailedWithItsBreakdown() {
		fichaWithEmail("ana@example.com");
		ConfirmPaymentResult result = new ConfirmPaymentResult(CANDIDATE_ID, "ADM-2026-000001", CandidateStatus.PAID,
				"REF-C1", new BigDecimal("100.00"), Instant.now(), "REC-C", new BigDecimal("1850.00"),
				new BigDecimal("1750.00"), "REC-OLD", Instant.now(), true);

		listener.onPaymentConfirmed(new CandidateMailEvents.PaymentConfirmed(result));

		ArgumentCaptor<CandidateFichaMailService.Breakdown> breakdown = ArgumentCaptor
				.forClass(CandidateFichaMailService.Breakdown.class);
		verify(mailService).sendPaymentConfirmation(eq("ana@example.com"), any(), eq("ADM-2026-000001"), any(),
				eq(new BigDecimal("100.00")), eq("REF-C1"), any(), eq("REC-C"), breakdown.capture());
		assertThat(breakdown.getValue().previousReceipt()).isEqualTo("REC-OLD");
		assertThat(breakdown.getValue().programChange()).isTrue();
	}

	@Test
	void aDifferenceNoticeLinksToThePortal() {
		fichaWithEmail("ana@example.com");

		listener.onDifferenceLeft(new CandidateMailEvents.DifferenceLeft(CANDIDATE_ID, BigDecimal.TEN, BigDecimal.ONE,
				new BigDecimal("9"), Instant.now()));

		verify(mailService).sendProgramChangeDifference(eq("ana@example.com"), any(), any(), any(), any(), any(),
				any(), any(), eq("https://sisa.utez.edu.mx/SGA/portal/ficha/pago"));
	}

	@Test
	void aFichaWithoutEmailSendsNothing() {
		fichaWithEmail(" ");

		listener.onCreditLeft(new CandidateMailEvents.CreditLeft(CANDIDATE_ID, BigDecimal.TEN));

		verifyNoInteractions(mailService);
	}

	@Test
	void noBaseUrlMeansNoLink() {
		assertThat(CandidateMailListener.portalUrl("")).isNull();
		assertThat(CandidateMailListener.portalUrl("https://h/SGA")).isEqualTo("https://h/SGA/portal/ficha/pago");
	}
}
