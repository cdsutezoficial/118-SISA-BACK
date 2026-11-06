package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentCredit;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentCreditRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A lapsed ficha is cancelled, and what it had received goes back as saldo a favor. */
@ExtendWith(MockitoExtension.class)
class CancelDebtUseCaseImplTest {

	private static final Instant NOW = Instant.parse("2026-11-04T06:10:00Z");

	private static final UUID CANDIDATE_ID = UUID.randomUUID();

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private PaymentCreditRepository paymentCreditRepository;

	private CancelDebtUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new CancelDebtUseCaseImpl(paymentRepository, paymentCreditRepository,
				Clock.fixed(NOW, ZoneId.of("UTC")));
	}

	@Test
	void anUnpaidFichaIsCancelledWithNoSaldo() {
		Payment ficha = charge("1500.00", "0.00", PaymentStatus.PENDING);

		assertThat(useCase.cancel(ficha.getId())).isTrue();

		assertThat(ficha.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
		verify(paymentCreditRepository, never()).save(any());
	}

	@Test
	void aFichaPartlyPaidWithSaldoGivesItBack() {
		Payment ficha = charge("1500.00", "200.00", PaymentStatus.PARTIAL);

		assertThat(useCase.cancel(ficha.getId())).isTrue();

		assertThat(ficha.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
		ArgumentCaptor<PaymentCredit> credit = ArgumentCaptor.forClass(PaymentCredit.class);
		verify(paymentCreditRepository).save(credit.capture());
		assertThat(credit.getValue().getAmount()).isEqualByComparingTo("200.00");
		assertThat(credit.getValue().getOriginPaymentId()).isEqualTo(ficha.getId());
		assertThat(credit.getValue().getCandidateId()).isEqualTo(CANDIDATE_ID);
		assertThat(credit.getValue().isAvailable()).isTrue();
	}

	@Test
	void aPaidFichaIsLeftAlone() {
		Payment ficha = charge("1500.00", "1500.00", PaymentStatus.PAID);

		assertThat(useCase.cancel(ficha.getId())).isFalse();

		assertThat(ficha.getStatus()).isEqualTo(PaymentStatus.PAID);
		verify(paymentCreditRepository, never()).save(any());
	}

	private Payment charge(String due, String paid, PaymentStatus status) {
		Payment payment = new Payment(CANDIDATE_ID, null, "FICHA_ING", null, null, UUID.randomUUID(),
				new BigDecimal(due), new BigDecimal(due), null, PaymentStatus.PENDING, PaymentOrigin.ADMISSION,
				NOW, NOW.minusSeconds(86_400));
		ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
		payment.settle(new BigDecimal(paid), status);
		when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
		return payment;
	}
}
