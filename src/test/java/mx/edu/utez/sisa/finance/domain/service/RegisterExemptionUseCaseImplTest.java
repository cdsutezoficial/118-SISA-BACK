package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.CreatePaymentUseCase.PaymentResult;
import mx.edu.utez.sisa.finance.domain.port.in.CreatePaymentUseCase.PaymentSnapshotStatus;
import mx.edu.utez.sisa.finance.domain.port.in.RegisterExemptionUseCase.RegisterExemptionCommand;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentEntryRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotPayableException;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterExemptionUseCaseImplTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"),
			ZoneId.of("America/Mexico_City"));

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private PaymentEntryRepository paymentEntryRepository;

	@Mock
	private PaymentBenefitRepository paymentBenefitRepository;

	private RegisterExemptionUseCaseImpl useCase;

	private UUID personId;

	@BeforeEach
	void setUp() {
		useCase = new RegisterExemptionUseCaseImpl(paymentRepository, paymentEntryRepository,
				paymentBenefitRepository, CLOCK);
		personId = UUID.randomUUID();
	}

	@Test
	void registersZeroEntryOnExemptPayment() {
		Payment payment = payment(PaymentStatus.EXEMPT, new BigDecimal("0.00"), null);
		when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));

		PaymentResult result = useCase.registerExemption(new RegisterExemptionCommand(payment.getId(), "REC-1", null,
				UUID.randomUUID(), "Ventanilla"));

		assertThat(result.status()).isEqualTo(PaymentSnapshotStatus.EXEMPT);
		verify(paymentEntryRepository).save(any());
	}

	@Test
	void rejectsNonExemptPayment() {
		Payment payment = payment(PaymentStatus.QUOTED, new BigDecimal("1000.00"), null);
		when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));

		assertThatThrownBy(() -> useCase.registerExemption(new RegisterExemptionCommand(payment.getId(), null, null,
				UUID.randomUUID(), null))).isInstanceOf(PaymentNotPayableException.class);
		verify(paymentEntryRepository, never()).save(any());
	}

	private Payment payment(PaymentStatus status, BigDecimal amountDue, UUID benefitId) {
		Payment payment = new Payment(personId, null, "TUITION", PaymentConceptType.PERIODIC_QUOTA, null, null,
				UUID.randomUUID(), null, amountDue, amountDue, benefitId, status, PaymentOrigin.CASHIER, null,
				Instant.parse("2026-10-19T00:00:00Z"), UUID.randomUUID());
		ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
		return payment;
	}
}
