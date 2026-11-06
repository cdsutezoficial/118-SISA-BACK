package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentCredit;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.RegisterOverpaymentUseCase.Overpayment;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentCreditRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentEntryRepository;
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

/**
 * Unit tests for {@link RegisterOverpaymentUseCaseImpl}: a second capture becomes saldo
 * a favor of the charge's owner, once per order, and never for the order that paid.
 */
@ExtendWith(MockitoExtension.class)
class RegisterOverpaymentUseCaseImplTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"),
			ZoneId.of("America/Mexico_City"));

	private static final String ORDER_ID = "TESTUTEZ-ADM-2026-000001-B";

	private static final BigDecimal CAPTURED = new BigDecimal("1750.00");

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private PaymentEntryRepository paymentEntryRepository;

	@Mock
	private PaymentCreditRepository paymentCreditRepository;

	private RegisterOverpaymentUseCaseImpl useCase;

	private UUID candidateId;

	@BeforeEach
	void setUp() {
		useCase = new RegisterOverpaymentUseCaseImpl(paymentRepository, paymentEntryRepository,
				paymentCreditRepository, CLOCK);
		candidateId = UUID.randomUUID();
	}

	@Test
	void aSecondCaptureBecomesSaldoAFavorOfTheChargeOwner() {
		Payment ficha = paidFicha();
		when(paymentEntryRepository.existsByExternalOrderId(ORDER_ID)).thenReturn(false);
		when(paymentCreditRepository.findByExternalOrderId(ORDER_ID)).thenReturn(Optional.empty());
		when(paymentRepository.findById(ficha.getId())).thenReturn(Optional.of(ficha));
		when(paymentCreditRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		Optional<Overpayment> result = useCase.register(ficha.getId(), ORDER_ID, CAPTURED);

		ArgumentCaptor<PaymentCredit> saved = ArgumentCaptor.forClass(PaymentCredit.class);
		verify(paymentCreditRepository).save(saved.capture());
		PaymentCredit credit = saved.getValue();
		assertThat(credit.getCandidateId()).isEqualTo(candidateId);
		assertThat(credit.getOriginPaymentId()).isEqualTo(ficha.getId());
		assertThat(credit.getExternalOrderId()).isEqualTo(ORDER_ID);
		assertThat(credit.getAmount()).isEqualByComparingTo(CAPTURED);
		assertThat(credit.isAvailable()).isTrue();
		assertThat(result).hasValueSatisfying(overpayment -> assertThat(overpayment.created()).isTrue());
	}

	/** A second run of the reconciliation must not book the same order twice. */
	@Test
	void anOrderAlreadyBookedReturnsItsCreditWithoutCreatingAnother() {
		PaymentCredit booked = PaymentCredit.overpayment(candidateId, null, UUID.randomUUID(), ORDER_ID, CAPTURED,
				Instant.parse("2026-11-02T06:05:00Z"));
		ReflectionTestUtils.setField(booked, "id", UUID.randomUUID());
		when(paymentEntryRepository.existsByExternalOrderId(ORDER_ID)).thenReturn(false);
		when(paymentCreditRepository.findByExternalOrderId(ORDER_ID)).thenReturn(Optional.of(booked));

		Optional<Overpayment> result = useCase.register(UUID.randomUUID(), ORDER_ID, CAPTURED);

		assertThat(result).hasValueSatisfying(overpayment -> {
			assertThat(overpayment.creditId()).isEqualTo(booked.getId());
			assertThat(overpayment.created()).isFalse();
		});
		verify(paymentCreditRepository, never()).save(any());
	}

	/** The order that paid the charge has its abono: nothing of it is left over. */
	@Test
	void theOrderThatPaidTheChargeBooksNothing() {
		when(paymentEntryRepository.existsByExternalOrderId(ORDER_ID)).thenReturn(true);

		Optional<Overpayment> result = useCase.register(UUID.randomUUID(), ORDER_ID, CAPTURED);

		assertThat(result).isEmpty();
		verify(paymentCreditRepository, never()).save(any());
	}

	private Payment paidFicha() {
		Payment payment = new Payment(candidateId, null, "ADMISSION_FICHA", null, null, UUID.randomUUID(), CAPTURED,
				CAPTURED, null, PaymentStatus.PAID, PaymentOrigin.ADMISSION, null,
				Instant.parse("2026-11-01T00:00:00Z"));
		ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
		return payment;
	}
}
