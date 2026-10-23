package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.finance.domain.port.out.CheckoutAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmAdmissionPaymentUseCaseImplTest {

	private static final BigDecimal AMOUNT = new BigDecimal("500.00");

	private static final UUID CHARGE_ID = UUID.randomUUID();

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private FichaPaymentPort fichaPaymentPort;

	@Mock
	private CheckoutAttemptRepository checkoutAttemptRepository;

	private ConfirmAdmissionPaymentUseCaseImpl useCase;

	private UUID candidateId;
	private Candidate candidate;

	@BeforeEach
	void setUp() {
		useCase = new ConfirmAdmissionPaymentUseCaseImpl(candidateRepository, fichaPaymentPort,
				checkoutAttemptRepository);
		candidateId = UUID.randomUUID();
		candidate = new Candidate(UUID.randomUUID(), UUID.randomUUID(), "ADM-2026-000001", true, true, null);
		ReflectionTestUtils.setField(candidate, "id", candidateId);
		candidate.linkFichaPayment(CHARGE_ID);
	}

	private void stubHappyPath() {
		when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
		when(fichaPaymentPort.findCharge(CHARGE_ID)).thenReturn(Optional.of(FichaTestData.pendingCharge(CHARGE_ID)));
		when(candidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(fichaPaymentPort.settle(any())).thenReturn(new FichaPaymentPort.SettledFicha(AMOUNT,
				BigDecimal.ZERO.setScale(2), AMOUNT, AMOUNT, null, false));
	}

	@Test
	void confirm_marksPaymentAndCandidatePaidWithReceipt_andBooksTheSettledAmount() {
		stubHappyPath();

		var result = useCase.confirm(candidateId, null);

		assertThat(result.candidateStatus()).isEqualTo(CandidateStatus.PAID);
		assertThat(result.receiptNumber()).isNotNull().startsWith("REC-");
		assertThat(result.amount()).isEqualByComparingTo(AMOUNT);
		assertThat(result.paidAt()).isNotNull();
		assertThat(result.referenceNumber()).isEqualTo(FichaTestData.REFERENCE);
		ArgumentCaptor<FichaPaymentPort.SettleFichaCommand> settled = ArgumentCaptor
				.forClass(FichaPaymentPort.SettleFichaCommand.class);
		verify(fichaPaymentPort).settle(settled.capture());
		assertThat(settled.getValue().receiptNumber()).isEqualTo(result.receiptNumber());

		ArgumentCaptor<Candidate> candidateCaptor = ArgumentCaptor.forClass(Candidate.class);
		verify(candidateRepository).save(candidateCaptor.capture());
		assertThat(candidateCaptor.getValue().getStatus()).isEqualTo(CandidateStatus.PAID);
		assertThat(candidateCaptor.getValue().getPaidAt()).isNotNull();
	}

	@Test
	void confirm_withoutAttempt_letsFinancePriceLive() {
		stubHappyPath();

		useCase.confirm(candidateId, null);

		ArgumentCaptor<FichaPaymentPort.SettleFichaCommand> command = ArgumentCaptor
				.forClass(FichaPaymentPort.SettleFichaCommand.class);
		verify(fichaPaymentPort).settle(command.capture());
		assertThat(command.getValue().financePaymentId()).isEqualTo(CHARGE_ID);
		assertThat(command.getValue().charged()).isNull();
		assertThat(command.getValue().online()).isFalse();
		verify(checkoutAttemptRepository, never()).findByOrderId(any());
	}

	/**
	 * The ficha was re-quoted by a second checkout (discount granted in
	 * between), but the bank captured the FIRST attempt: the numbers booked are
	 * the captured attempt's snapshot, not the latest quote.
	 */
	@Test
	void confirm_withCapturedAttempt_settlesWithThatAttemptsSnapshot() {
		stubHappyPath();
		UUID benefitId = UUID.randomUUID();
		UUID rateId = UUID.randomUUID();
		CheckoutAttempt first = new CheckoutAttempt("ORD-1", CHARGE_ID, candidateId, null, new BigDecimal("400.00"),
				Instant.now(), rateId, AMOUNT, new BigDecimal("100.00"), benefitId);
		when(checkoutAttemptRepository.findByOrderId("ORD-1")).thenReturn(Optional.of(first));

		useCase.confirm(candidateId, "ORD-1");

		ArgumentCaptor<FichaPaymentPort.SettleFichaCommand> command = ArgumentCaptor
				.forClass(FichaPaymentPort.SettleFichaCommand.class);
		verify(fichaPaymentPort).settle(command.capture());
		FichaPaymentPort.ChargedAmounts charged = command.getValue().charged();
		assertThat(charged.amountDue()).isEqualByComparingTo("400.00");
		assertThat(charged.originalAmount()).isEqualByComparingTo(AMOUNT);
		assertThat(charged.discountAmount()).isEqualByComparingTo("100.00");
		assertThat(charged.benefitId()).isEqualTo(benefitId);
		assertThat(charged.rateId()).isEqualTo(rateId);
		assertThat(command.getValue().online()).isTrue();
		assertThat(command.getValue().externalOrderId()).isEqualTo("ORD-1");
	}

	@Test
	void confirm_unknownCandidateThrowsNotFound() {
		when(candidateRepository.findById(candidateId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.confirm(candidateId, null)).isInstanceOf(CandidateNotFoundException.class);
	}

	@Test
	void confirm_alreadyPaidThrowsConflict_andBooksNothing() {
		candidate.markPaid();
		when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
		when(fichaPaymentPort.findCharge(CHARGE_ID)).thenReturn(Optional.of(FichaTestData.paidCharge(CHARGE_ID)));

		assertThatThrownBy(() -> useCase.confirm(candidateId, null)).isInstanceOf(CandidateAlreadyPaidException.class);
		verify(fichaPaymentPort, never()).settle(any());
	}
}
