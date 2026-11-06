package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.BenefitMode;
import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.UpdateBenefitUseCase.UpdateBenefitCommand;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.BenefitNotEditableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateBenefitUseCaseImplTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"),
			ZoneId.of("America/Mexico_City"));

	@Mock
	private PaymentBenefitRepository paymentBenefitRepository;

	@Mock
	private PaymentRepository paymentRepository;

	/** Charge replaced by a career change → the charge that replaced it. */
	private final Map<UUID, UUID> replacements = new HashMap<>();

	private final UUID candidateId = UUID.randomUUID();

	private UpdateBenefitUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new UpdateBenefitUseCaseImpl(paymentBenefitRepository, paymentRepository,
				new ReplacedChargeGuard(id -> Optional.ofNullable(replacements.get(id)), paymentRepository), CLOCK);
	}

	/** A discount left on a charge that a career change replaced is not edited there. */
	@Test
	void aDiscountOnAReplacedChargeIsNotEdited() {
		Payment replaced = charge();
		Payment difference = charge();
		replacements.put(replaced.getId(), difference.getId());
		PaymentBenefit benefit = discountOn(replaced.getId());
		when(paymentBenefitRepository.findById(benefit.getId())).thenReturn(Optional.of(benefit));
		when(paymentRepository.findById(difference.getId())).thenReturn(Optional.of(difference));

		assertThatThrownBy(() -> useCase.updateBenefit(update(benefit.getId())))
				.isInstanceOf(BenefitNotEditableException.class)
				.hasMessage(ReplacedChargeGuard.APPLY_TO_DIFFERENCE);
		verify(paymentBenefitRepository, never()).save(any());
	}

	@Test
	void aDiscountOnTheChargeBeingCollectedIsEdited() {
		Payment charge = charge();
		PaymentBenefit benefit = discountOn(charge.getId());
		when(paymentBenefitRepository.findById(benefit.getId())).thenReturn(Optional.of(benefit));
		when(paymentRepository.findById(charge.getId())).thenReturn(Optional.of(charge));
		when(paymentBenefitRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		useCase.updateBenefit(update(benefit.getId()));

		assertThat(benefit.getPercent()).isEqualByComparingTo("20");
	}

	private Payment charge() {
		Payment charge = new Payment(candidateId, null, "FICHA-ING", null, null, UUID.randomUUID(), null, null, null,
				PaymentStatus.PENDING, PaymentOrigin.ADMISSION, null, Instant.parse("2026-10-01T00:00:00Z"));
		ReflectionTestUtils.setField(charge, "id", UUID.randomUUID());
		return charge;
	}

	private PaymentBenefit discountOn(UUID paymentId) {
		PaymentBenefit benefit = new PaymentBenefit(paymentId, candidateId, null, "FICHA-ING", null, null,
				BenefitMode.PERCENT, new BigDecimal("10"), null, LocalDate.parse("2026-10-01"),
				LocalDate.parse("2026-12-31"), "promedio", UUID.randomUUID(), "Servicios Escolares", Instant.now());
		ReflectionTestUtils.setField(benefit, "id", UUID.randomUUID());
		return benefit;
	}

	private UpdateBenefitCommand update(UUID benefitId) {
		return new UpdateBenefitCommand(benefitId, null, null, BenefitMode.PERCENT, new BigDecimal("20"), null,
				LocalDate.parse("2026-10-01"), LocalDate.parse("2026-12-31"), "promedio corregido");
	}
}
