package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentChannel;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntry;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntryMethod;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentEntryRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetChargeStatusUseCaseImplTest {

	private static final UUID PAYMENT_ID = UUID.randomUUID();

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private PaymentEntryRepository paymentEntryRepository;

	private Payment charge(PaymentStatus status) {
		Payment payment = new Payment(UUID.randomUUID(), null, "FICHA-ING", null, null, UUID.randomUUID(), null,
				null, null, status, PaymentOrigin.ADMISSION, Instant.parse("2026-10-05T05:59:59Z"),
				Instant.parse("2026-09-25T12:00:00Z"));
		ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
		payment.assignReference("REF-ADM-2026-000001");
		return payment;
	}

	@Test
	void aPendingChargeReportsItsHeaderAndNoReceipt() {
		when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(charge(PaymentStatus.PENDING)));
		when(paymentEntryRepository.findByPaymentIdOrderByPaidAtAsc(PAYMENT_ID)).thenReturn(List.of());

		var status = new GetChargeStatusUseCaseImpl(paymentRepository, paymentEntryRepository).find(PAYMENT_ID)
				.orElseThrow();

		assertThat(status.referenceNumber()).isEqualTo("REF-ADM-2026-000001");
		assertThat(status.status()).isEqualTo(PaymentStatus.PENDING);
		assertThat(status.dueAt()).isEqualTo(Instant.parse("2026-10-05T05:59:59Z"));
		assertThat(status.paidAt()).isNull();
		assertThat(status.receiptNumber()).isNull();
	}

	@Test
	void aPaidChargeReportsTheReceiptOfItsLastEntry() {
		when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(charge(PaymentStatus.PAID)));
		Instant paidAt = Instant.parse("2026-09-26T15:00:00Z");
		when(paymentEntryRepository.findByPaymentIdOrderByPaidAtAsc(PAYMENT_ID)).thenReturn(List.of(
				new PaymentEntry(PAYMENT_ID, new BigDecimal("500.00"), PaymentEntryMethod.ONLINE, PaymentChannel.PORTAL,
						"EVO", "ORD-1", null, "REF-ADM-2026-000001", "REC-20260926-000001", false, null, null, paidAt,
						null)));

		var status = new GetChargeStatusUseCaseImpl(paymentRepository, paymentEntryRepository).find(PAYMENT_ID)
				.orElseThrow();

		assertThat(status.receiptNumber()).isEqualTo("REC-20260926-000001");
		assertThat(status.paidAt()).isEqualTo(paidAt);
	}

	@Test
	void anUnknownOrNullChargeIsEmpty() {
		var useCase = new GetChargeStatusUseCaseImpl(paymentRepository, paymentEntryRepository);
		when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

		assertThat(useCase.find(PAYMENT_ID)).isEmpty();
		assertThat(useCase.find(null)).isEmpty();
	}
}
