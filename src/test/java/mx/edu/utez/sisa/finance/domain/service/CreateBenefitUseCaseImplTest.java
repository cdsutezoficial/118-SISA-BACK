package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.BenefitMode;
import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.port.in.BenefitView;
import mx.edu.utez.sisa.finance.domain.port.in.CreateBenefitUseCase.CreateBenefitCommand;
import mx.edu.utez.sisa.finance.domain.port.out.AnchorQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.shared.exception.InvalidBenefitDataException;
import mx.edu.utez.sisa.finance.shared.exception.PersonNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateBenefitUseCaseImplTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"),
			ZoneId.of("America/Mexico_City"));

	@Mock
	private AnchorQueryPort anchorQueryPort;

	@Mock
	private PaymentBenefitRepository paymentBenefitRepository;

	@Mock
	private RetroactiveDiscountGranter retroactiveDiscountGranter;

	private CreateBenefitUseCaseImpl useCase;

	private UUID candidateId;

	@BeforeEach
	void setUp() {
		useCase = new CreateBenefitUseCaseImpl(anchorQueryPort, paymentBenefitRepository, retroactiveDiscountGranter,
				CLOCK);
		candidateId = UUID.randomUUID();
	}

	@Test
	void missingMode_failsValidation() {
		assertThatThrownBy(() -> useCase.createBenefit(command(null, null, null)))
				.isInstanceOf(InvalidBenefitDataException.class);

		verify(paymentBenefitRepository, never()).save(any());
	}

	@Test
	void percentOutOfRange_failsValidation() {
		assertThatThrownBy(() -> useCase.createBenefit(command(BenefitMode.PERCENT, new BigDecimal("150"), null)))
				.isInstanceOf(InvalidBenefitDataException.class);
	}

	@Test
	void unknownAnchor_fails() {
		when(anchorQueryPort.candidateExists(candidateId)).thenReturn(false);

		assertThatThrownBy(() -> useCase.createBenefit(command(BenefitMode.PERCENT, new BigDecimal("50"), null)))
				.isInstanceOf(PersonNotFoundException.class);
	}

	@Test
	void createsBenefitAndGrantsRetroactiveCredit() {
		when(anchorQueryPort.candidateExists(candidateId)).thenReturn(true);
		when(paymentBenefitRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		BenefitView view = useCase.createBenefit(command(BenefitMode.PERCENT, new BigDecimal("50"), null));

		assertThat(view.conceptCode()).isEqualTo("TUITION");
		assertThat(view.mode()).isEqualTo(BenefitMode.PERCENT);
		assertThat(view.percent()).isEqualByComparingTo("50");
		assertThat(view.inForce()).isTrue();
		verify(retroactiveDiscountGranter).grant(any(PaymentBenefit.class), any());
	}

	private CreateBenefitCommand command(BenefitMode mode, BigDecimal percent, BigDecimal amount) {
		return new CreateBenefitCommand(candidateId, null, "TUITION", null, null, mode, percent, amount,
				LocalDate.parse("2026-10-01"), LocalDate.parse("2026-12-31"), "beca", UUID.randomUUID(), "Finanzas");
	}
}
