package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentChannel;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntryMethod;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.QuoteDebtUseCase.DebtQuote;
import mx.edu.utez.sisa.finance.domain.port.in.RegisterExemptionUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.RegisterPaymentEntryUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.SettleDebtUseCase.ChargedSnapshot;
import mx.edu.utez.sisa.finance.domain.port.in.SettleDebtUseCase.SettleDebtCommand;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotPayableException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Settling books what the payer was actually charged: the captured attempt's
 * snapshot, or the live price frozen now when there is no attempt.
 */
@ExtendWith(MockitoExtension.class)
class SettleDebtUseCaseImplTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T18:00:00Z"),
			ZoneId.of("America/Mexico_City"));

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private DebtQuoter debtQuoter;

	@Mock
	private RegisterPaymentEntryUseCase registerPaymentEntryUseCase;

	@Mock
	private RegisterExemptionUseCase registerExemptionUseCase;

	private SettleDebtUseCaseImpl useCase;

	private Payment charge;

	@BeforeEach
	void setUp() {
		FreezeDebtQuoteUseCaseImpl freeze = new FreezeDebtQuoteUseCaseImpl(paymentRepository, debtQuoter, CLOCK);
		useCase = new SettleDebtUseCaseImpl(paymentRepository, freeze, registerPaymentEntryUseCase,
				registerExemptionUseCase, CLOCK);
		charge = new Payment(UUID.randomUUID(), null, "FICHA-ING", null, null, UUID.randomUUID(), null, null, null,
				PaymentStatus.PENDING, PaymentOrigin.ADMISSION, null, Instant.parse("2026-10-01T00:00:00Z"));
		ReflectionTestUtils.setField(charge, "id", UUID.randomUUID());
		when(paymentRepository.findByIdForUpdate(charge.getId())).thenReturn(Optional.of(charge));
		lenient().when(paymentRepository.findById(charge.getId())).thenReturn(Optional.of(charge));
		lenient().when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	private SettleDebtCommand online(ChargedSnapshot snapshot) {
		return new SettleDebtCommand(charge.getId(), snapshot, PaymentEntryMethod.ONLINE, PaymentChannel.PORTAL,
				"EVO", "ORD-1", "REC-1", "REF-1", null, "Pago en línea");
	}

	/**
	 * Attempt 1 was opened at $1120; a 50 % discount was granted and attempt 2
	 * re-froze the charge at $560; the bank captured attempt 1. What gets booked
	 * is attempt 1's $1120 — the money that actually moved.
	 */
	@Test
	void theCapturedAttemptsSnapshotWinsOverALaterReQuote() {
		charge.reprice(new BigDecimal("1120.00"), new BigDecimal("560.00"), UUID.randomUUID(), null,
				PaymentStatus.PENDING);
		UUID rateId = UUID.randomUUID();
		ChargedSnapshot attempt1 = new ChargedSnapshot(rateId, new BigDecimal("1120.00"), new BigDecimal("0.00"),
				new BigDecimal("1120.00"), null);

		useCase.settle(online(attempt1));

		assertThat(charge.getAmountDue()).isEqualByComparingTo("1120.00");
		assertThat(charge.getBenefitId()).isNull();
		assertThat(charge.getRateId()).isEqualTo(rateId);
		ArgumentCaptor<RegisterPaymentEntryUseCase.RegisterEntryCommand> entry = ArgumentCaptor
				.forClass(RegisterPaymentEntryUseCase.RegisterEntryCommand.class);
		verify(registerPaymentEntryUseCase).registerEntry(entry.capture());
		assertThat(entry.getValue().amount()).isEqualByComparingTo("1120.00");
		assertThat(entry.getValue().externalOrderId()).isEqualTo("ORD-1");
		assertThat(entry.getValue().method()).isEqualTo(PaymentEntryMethod.ONLINE);
	}

	@Test
	void withoutASnapshotTheLivePriceIsFrozenAndBooked() {
		when(debtQuoter.live(any(), any())).thenReturn(new DebtQuote(charge.getId(), charge.getCandidateId(), null,
				"FICHA-ING", "Ficha", PaymentStatus.PENDING, UUID.randomUUID(), new BigDecimal("1120.00"),
				new BigDecimal("112.00"), new BigDecimal("1008.00"), BigDecimal.ZERO.setScale(2), null, false, true,
				null, null));

		useCase.settle(new SettleDebtCommand(charge.getId(), null, PaymentEntryMethod.CASH, PaymentChannel.CASHIER,
				null, null, "REC-2", "REF-1", UUID.randomUUID(), "Caja"));

		assertThat(charge.getAmountDue()).isEqualByComparingTo("1008.00");
		ArgumentCaptor<RegisterPaymentEntryUseCase.RegisterEntryCommand> entry = ArgumentCaptor
				.forClass(RegisterPaymentEntryUseCase.RegisterEntryCommand.class);
		verify(registerPaymentEntryUseCase).registerEntry(entry.capture());
		assertThat(entry.getValue().amount()).isEqualByComparingTo("1008.00");
		assertThat(entry.getValue().isPhysical()).isTrue();
	}

	@Test
	void aZeroSnapshotBooksTheExemptionNotAnAbono() {
		ChargedSnapshot full = new ChargedSnapshot(null, new BigDecimal("1120.00"), new BigDecimal("1120.00"),
				new BigDecimal("0.00"), UUID.randomUUID());

		useCase.settle(online(full));

		verify(registerExemptionUseCase).registerExemption(any());
		verify(registerPaymentEntryUseCase, never()).registerEntry(any());
	}

	@Test
	void settlingAPaidChargeIsANoOp() {
		charge.reprice(new BigDecimal("1120.00"), new BigDecimal("1120.00"), null, null, PaymentStatus.PENDING);
		charge.settle(new BigDecimal("1120.00"), PaymentStatus.PAID);

		useCase.settle(online(null));

		verify(registerPaymentEntryUseCase, never()).registerEntry(any());
		verify(registerExemptionUseCase, never()).registerExemption(any());
	}

	@Test
	void aMismatchOnAChargeThatAlreadyHasAbonosIsRefused() {
		charge.reprice(new BigDecimal("1120.00"), new BigDecimal("1120.00"), null, null, PaymentStatus.PENDING);
		charge.settle(new BigDecimal("500.00"), PaymentStatus.PARTIAL);
		ChargedSnapshot other = new ChargedSnapshot(null, new BigDecimal("1120.00"), new BigDecimal("560.00"),
				new BigDecimal("560.00"), UUID.randomUUID());

		assertThatThrownBy(() -> useCase.settle(online(other))).isInstanceOf(PaymentNotPayableException.class);
		verify(registerPaymentEntryUseCase, never()).registerEntry(any());
	}
}
