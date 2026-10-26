package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.BenefitMode;
import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.model.PaymentCredit;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentCreditRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A discount granted on a charge already paid turns the difference into saldo a favor. */
@ExtendWith(MockitoExtension.class)
class RetroactiveDiscountGranterTest {

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private PaymentCreditRepository paymentCreditRepository;

	private final UUID candidateId = UUID.randomUUID();

	private Payment paidCharge() {
		Payment charge = new Payment(candidateId, null, "FICHA-ING", null, null, UUID.randomUUID(),
				new BigDecimal("1120.00"), new BigDecimal("1120.00"), null, PaymentStatus.PENDING,
				PaymentOrigin.ADMISSION, null, Instant.parse("2026-10-01T00:00:00Z"));
		ReflectionTestUtils.setField(charge, "id", UUID.randomUUID());
		charge.settle(new BigDecimal("1120.00"), PaymentStatus.PAID);
		return charge;
	}

	private PaymentBenefit halfOff(UUID paymentId) {
		PaymentBenefit benefit = new PaymentBenefit(paymentId, candidateId, null, "FICHA-ING", null, null,
				BenefitMode.PERCENT, new BigDecimal("50"), null, LocalDate.parse("2026-10-01"),
				LocalDate.parse("2026-12-31"), "promedio", UUID.randomUUID(), "Servicios Escolares", Instant.now());
		ReflectionTestUtils.setField(benefit, "id", UUID.randomUUID());
		return benefit;
	}

	@Test
	void aDiscountOnAPaidChargeLeavesTheDifferenceAsCredit() {
		Payment charge = paidCharge();
		when(paymentRepository.findById(charge.getId())).thenReturn(Optional.of(charge));
		when(paymentCreditRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		RetroactiveDiscountGranter granter = new RetroactiveDiscountGranter(paymentRepository,
				paymentCreditRepository, new PaymentQuoter());

		List<PaymentCredit> credits = granter.grant(halfOff(charge.getId()), Instant.now());

		assertThat(credits).hasSize(1);
		assertThat(credits.get(0).getAmount()).isEqualByComparingTo("560.00");
		assertThat(credits.get(0).getOriginPaymentId()).isEqualTo(charge.getId());
		assertThat(credits.get(0).getCandidateId()).isEqualTo(candidateId);
	}

	@Test
	void anOpenChargeGetsNoCredit() {
		Payment open = new Payment(candidateId, null, "FICHA-ING", null, null, UUID.randomUUID(), null, null, null,
				PaymentStatus.PENDING, PaymentOrigin.ADMISSION, null, Instant.parse("2026-10-01T00:00:00Z"));
		ReflectionTestUtils.setField(open, "id", UUID.randomUUID());
		when(paymentRepository.findById(open.getId())).thenReturn(Optional.of(open));
		RetroactiveDiscountGranter granter = new RetroactiveDiscountGranter(paymentRepository,
				paymentCreditRepository, new PaymentQuoter());

		assertThat(granter.grant(halfOff(open.getId()), Instant.now())).isEmpty();
		verify(paymentCreditRepository, never()).save(any());
	}
}
