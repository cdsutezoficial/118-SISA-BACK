package mx.edu.utez.sisa.finance.domain.service;

import java.util.List;
import mx.edu.utez.sisa.finance.domain.model.BenefitMode;
import mx.edu.utez.sisa.finance.domain.model.BenefitStatus;
import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.port.in.BenefitView;
import mx.edu.utez.sisa.finance.domain.port.in.CreateBenefitUseCase.CreateBenefitCommand;
import mx.edu.utez.sisa.finance.domain.port.out.AnchorQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.BenefitNotEditableException;
import mx.edu.utez.sisa.finance.shared.exception.InvalidBenefitDataException;
import mx.edu.utez.sisa.finance.shared.exception.PersonNotFoundException;
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

	@Mock
	private PaymentRepository paymentRepository;

	private CreateBenefitUseCaseImpl useCase;

	private UUID candidateId;

	@BeforeEach
	void setUp() {
		useCase = new CreateBenefitUseCaseImpl(anchorQueryPort, paymentBenefitRepository, paymentRepository,
				retroactiveDiscountGranter, CLOCK);
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

	/**
	 * Staff pick a charge and grant the discount on it: anchor and concept are
	 * the charge's, whatever the request said, and the discount is linked to it.
	 */
	@Test
	void aDiscountOnAChargeTakesTheChargesAnchorAndConcept() {
		Payment charge = new Payment(candidateId, null, "FICHA-ING", null, null, UUID.randomUUID(), null, null, null,
				PaymentStatus.PENDING, PaymentOrigin.ADMISSION, null, Instant.parse("2026-10-01T00:00:00Z"));
		ReflectionTestUtils.setField(charge, "id", UUID.randomUUID());
		when(paymentRepository.findById(charge.getId())).thenReturn(Optional.of(charge));
		when(paymentBenefitRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		BenefitView view = useCase.createBenefit(new CreateBenefitCommand(null, null, null, null, null,
				BenefitMode.PERCENT, new BigDecimal("30"), null, LocalDate.parse("2026-10-01"),
				LocalDate.parse("2026-12-31"), "promedio", UUID.randomUUID(), "Servicios Escolares", charge.getId()));

		assertThat(view.paymentId()).isEqualTo(charge.getId());
		assertThat(view.candidateId()).isEqualTo(candidateId);
		assertThat(view.conceptCode()).isEqualTo("FICHA-ING");
		verify(retroactiveDiscountGranter).grant(any(PaymentBenefit.class), any());
	}

	@Test
	void aCancelledChargeCannotTakeADiscount() {
		Payment charge = new Payment(candidateId, null, "FICHA-ING", null, null, UUID.randomUUID(), null, null, null,
				PaymentStatus.PENDING, PaymentOrigin.ADMISSION, null, Instant.parse("2026-10-01T00:00:00Z"));
		ReflectionTestUtils.setField(charge, "id", UUID.randomUUID());
		charge.cancel();
		when(paymentRepository.findById(charge.getId())).thenReturn(Optional.of(charge));

		assertThatThrownBy(() -> useCase.createBenefit(new CreateBenefitCommand(null, null, null, null, null,
				BenefitMode.FULL, null, null, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-12-31"), "beca",
				UUID.randomUUID(), "Finanzas", charge.getId()))).isInstanceOf(InvalidBenefitDataException.class);
		verify(paymentBenefitRepository, never()).save(any());
	}

	private Payment charge(PaymentStatus status) {
		Payment charge = new Payment(candidateId, null, "FICHA-ING", null, null, UUID.randomUUID(),
				new BigDecimal("1120.00"), new BigDecimal("1120.00"), null, PaymentStatus.PENDING,
				PaymentOrigin.ADMISSION, null, Instant.parse("2026-10-01T00:00:00Z"));
		ReflectionTestUtils.setField(charge, "id", UUID.randomUUID());
		if (status == PaymentStatus.PAID) {
			charge.settle(new BigDecimal("1120.00"), PaymentStatus.PAID);
		}
		return charge;
	}

	private CreateBenefitCommand onCharge(Payment charge) {
		return new CreateBenefitCommand(null, null, null, null, null, BenefitMode.PERCENT, new BigDecimal("50"), null,
				LocalDate.parse("2026-10-01"), LocalDate.parse("2026-12-31"), "promedio", UUID.randomUUID(),
				"Servicios Escolares", charge.getId());
	}

	/** One discount per charge: a second one must be an edit of the first. */
	@Test
	void aChargeWithADiscountRefusesASecondOne() {
		Payment charge = charge(PaymentStatus.PENDING);
		when(paymentRepository.findById(charge.getId())).thenReturn(Optional.of(charge));
		PaymentBenefit existing = new PaymentBenefit(charge.getId(), candidateId, null, "FICHA-ING", null, null,
				BenefitMode.PERCENT, new BigDecimal("10"), null, LocalDate.parse("2026-10-01"),
				LocalDate.parse("2026-12-31"), "beca", UUID.randomUUID(), "Finanzas", Instant.now());
		when(paymentBenefitRepository.findByPaymentId(charge.getId())).thenReturn(List.of(existing));

		assertThatThrownBy(() -> useCase.createBenefit(onCharge(charge)))
				.isInstanceOf(BenefitNotEditableException.class)
				.hasMessageContaining("Edítalo");
		verify(paymentBenefitRepository, never()).save(any());
	}

	/**
	 * A discount on a charge already paid becomes saldo a favor right away, so it
	 * is consumed (USED) on the spot and can no longer be edited.
	 */
	@Test
	void aDiscountOnAPaidChargeGrantsCreditAndIsClosedToEdits() {
		Payment charge = charge(PaymentStatus.PAID);
		when(paymentRepository.findById(charge.getId())).thenReturn(Optional.of(charge));
		when(paymentBenefitRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		BenefitView view = useCase.createBenefit(onCharge(charge));

		verify(retroactiveDiscountGranter).grant(any(PaymentBenefit.class), any());
		assertThat(view.status()).isEqualTo(BenefitStatus.USED);
	}

	private CreateBenefitCommand command(BenefitMode mode, BigDecimal percent, BigDecimal amount) {
		return new CreateBenefitCommand(candidateId, null, "TUITION", null, null, mode, percent, amount,
				LocalDate.parse("2026-10-01"), LocalDate.parse("2026-12-31"), "beca", UUID.randomUUID(), "Finanzas", null);
	}
}
