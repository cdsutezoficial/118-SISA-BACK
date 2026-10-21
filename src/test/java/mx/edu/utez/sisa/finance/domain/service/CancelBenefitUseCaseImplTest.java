package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.BenefitMode;
import mx.edu.utez.sisa.finance.domain.model.BenefitStatus;
import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.port.in.BenefitView;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.BenefitNotEditableException;
import mx.edu.utez.sisa.finance.shared.exception.BenefitNotFoundException;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelBenefitUseCaseImplTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"),
			ZoneId.of("America/Mexico_City"));

	@Mock
	private PaymentBenefitRepository paymentBenefitRepository;

	@Mock
	private PaymentRepository paymentRepository;

	private CancelBenefitUseCaseImpl useCase;

	private UUID benefitId;

	@BeforeEach
	void setUp() {
		useCase = new CancelBenefitUseCaseImpl(paymentBenefitRepository, paymentRepository, CLOCK);
		benefitId = UUID.randomUUID();
	}

	@Test
	void unknownBenefit_fails() {
		when(paymentBenefitRepository.findById(benefitId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.cancelBenefit(benefitId)).isInstanceOf(BenefitNotFoundException.class);
	}

	@Test
	void cancelsActiveUnusedBenefit() {
		PaymentBenefit benefit = benefit();
		when(paymentBenefitRepository.findById(benefit.getId())).thenReturn(Optional.of(benefit));
		when(paymentRepository.existsByBenefitId(benefit.getId())).thenReturn(false);
		when(paymentBenefitRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		BenefitView view = useCase.cancelBenefit(benefit.getId());

		assertThat(view.status()).isEqualTo(BenefitStatus.CANCELLED);
	}

	@Test
	void refusesToCancelAppliedBenefit() {
		PaymentBenefit benefit = benefit();
		when(paymentBenefitRepository.findById(benefit.getId())).thenReturn(Optional.of(benefit));
		when(paymentRepository.existsByBenefitId(benefit.getId())).thenReturn(true);

		assertThatThrownBy(() -> useCase.cancelBenefit(benefit.getId()))
				.isInstanceOf(BenefitNotEditableException.class);
	}

	@Test
	void refusesToCancelNonActiveBenefit() {
		PaymentBenefit benefit = benefit();
		benefit.markUsed();
		when(paymentBenefitRepository.findById(benefit.getId())).thenReturn(Optional.of(benefit));

		assertThatThrownBy(() -> useCase.cancelBenefit(benefit.getId()))
				.isInstanceOf(BenefitNotEditableException.class);
	}

	private PaymentBenefit benefit() {
		PaymentBenefit benefit = new PaymentBenefit(UUID.randomUUID(), null, "TUITION", null, null, BenefitMode.PERCENT,
				new BigDecimal("50"), null, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-12-31"), "beca",
				UUID.randomUUID(), "Finanzas", Instant.parse("2026-10-19T00:00:00Z"));
		ReflectionTestUtils.setField(benefit, "id", UUID.randomUUID());
		return benefit;
	}
}
