package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentChannel;
import mx.edu.utez.sisa.finance.domain.model.PaymentCredit;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.ApplyCreditToPaymentUseCase.ApplyCreditCommand;
import mx.edu.utez.sisa.finance.domain.port.in.ApplyCreditToPaymentUseCase.CreditApplicationResult;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentCreditRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentEntryRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.CreditNotApplicableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplyCreditToPaymentUseCaseImplTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"),
			ZoneId.of("America/Mexico_City"));

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private PaymentEntryRepository paymentEntryRepository;

	@Mock
	private PaymentCreditRepository paymentCreditRepository;

	@Mock
	private PaymentBenefitRepository paymentBenefitRepository;

	@Mock
	private PaymentSettlementDecider settlementDecider;

	private ApplyCreditToPaymentUseCaseImpl useCase;

	private UUID candidateId;

	@BeforeEach
	void setUp() {
		useCase = new ApplyCreditToPaymentUseCaseImpl(paymentRepository, paymentEntryRepository,
				paymentCreditRepository, paymentBenefitRepository, settlementDecider, CLOCK);
		candidateId = UUID.randomUUID();
	}

	@Test
	void appliesAvailableCreditPartially() {
		Payment payment = payment(new BigDecimal("300.00"), PaymentStatus.PENDING);
		PaymentCredit credit = credit(new BigDecimal("150.00"));
		when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
		when(paymentEntryRepository.sumAmountByPaymentId(payment.getId())).thenReturn(ZERO)
				.thenReturn(new BigDecimal("150.00"));
		when(paymentCreditRepository.findAvailableByCandidateId(candidateId)).thenReturn(List.of(credit));
		when(paymentCreditRepository.findByIdForUpdate(credit.getId())).thenReturn(Optional.of(credit));
		when(settlementDecider.decide(any(), any())).thenReturn(PaymentStatus.PARTIAL);
		when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		CreditApplicationResult result = useCase
				.applyCredit(new ApplyCreditCommand(payment.getId(), PaymentChannel.PORTAL, UUID.randomUUID()));

		assertThat(result.creditApplied()).isEqualByComparingTo("150.00");
		assertThat(result.appliedCreditIds()).containsExactly(credit.getId());
		verify(paymentEntryRepository).save(any());
	}

	@Test
	void rejectsWhenNoCreditAvailable() {
		Payment payment = payment(new BigDecimal("300.00"), PaymentStatus.PENDING);
		when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
		when(paymentEntryRepository.sumAmountByPaymentId(payment.getId())).thenReturn(ZERO);
		when(paymentCreditRepository.findAvailableByCandidateId(candidateId)).thenReturn(List.of());

		assertThatThrownBy(() -> useCase
				.applyCredit(new ApplyCreditCommand(payment.getId(), PaymentChannel.PORTAL, UUID.randomUUID())))
				.isInstanceOf(CreditNotApplicableException.class);
		verify(paymentEntryRepository, never()).save(any());
	}

	@Test
	void rejectsPaymentThatDoesNotAcceptEntries() {
		Payment payment = payment(new BigDecimal("0.00"), PaymentStatus.EXEMPT);
		when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));

		assertThatThrownBy(() -> useCase
				.applyCredit(new ApplyCreditCommand(payment.getId(), PaymentChannel.PORTAL, UUID.randomUUID())))
				.isInstanceOf(CreditNotApplicableException.class);
	}

	private Payment payment(BigDecimal amountDue, PaymentStatus status) {
		Payment payment = new Payment(candidateId, null, "TUITION", null, null, UUID.randomUUID(), amountDue,
				amountDue, null, status, PaymentOrigin.CASHIER, null, Instant.parse("2026-10-19T00:00:00Z"));
		ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
		return payment;
	}

	private PaymentCredit credit(BigDecimal amount) {
		PaymentCredit credit = new PaymentCredit(candidateId, null, UUID.randomUUID(), UUID.randomUUID(), amount,
				Instant.parse("2026-10-19T00:00:00Z"));
		ReflectionTestUtils.setField(credit, "id", UUID.randomUUID());
		return credit;
	}
}
