package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentChannel;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntryMethod;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.CreatePaymentUseCase.PaymentResult;
import mx.edu.utez.sisa.finance.domain.port.in.CreatePaymentUseCase.PaymentSnapshotStatus;
import mx.edu.utez.sisa.finance.domain.port.in.RegisterPaymentEntryUseCase.RegisterEntryCommand;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentEntryRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.InvalidEntryAmountException;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotFoundException;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotPayableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class RegisterPaymentEntryUseCaseImplTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"),
			ZoneId.of("America/Mexico_City"));

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private PaymentEntryRepository paymentEntryRepository;

	@Mock
	private PaymentBenefitRepository paymentBenefitRepository;

	private RegisterPaymentEntryUseCaseImpl useCase;

	private UUID personId;
	private UUID programId;
	private UUID collectorId;

	@BeforeEach
	void setUp() {
		useCase = new RegisterPaymentEntryUseCaseImpl(paymentRepository, paymentEntryRepository,
				paymentBenefitRepository, new PaymentSettlementDecider(), CLOCK);
		personId = UUID.randomUUID();
		programId = UUID.randomUUID();
		collectorId = UUID.randomUUID();
	}

	@Test
	void nonPositiveAmount_failsValidation() {
		UUID paymentId = UUID.randomUUID();

		assertThatThrownBy(() -> useCase.registerEntry(command(paymentId, "0.00")))
				.isInstanceOf(InvalidEntryAmountException.class);

		verify(paymentRepository, never()).findByIdForUpdate(any());
	}

	@Test
	void unknownPayment_fails() {
		UUID paymentId = UUID.randomUUID();
		when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.registerEntry(command(paymentId, "100.00")))
				.isInstanceOf(PaymentNotFoundException.class);
	}

	@Test
	void paymentNotAcceptingEntries_fails() {
		UUID paymentId = UUID.randomUUID();
		when(paymentRepository.findByIdForUpdate(paymentId))
				.thenReturn(Optional.of(payment(paymentId, "1000.00", PaymentStatus.EXEMPT)));

		assertThatThrownBy(() -> useCase.registerEntry(command(paymentId, "100.00")))
				.isInstanceOf(PaymentNotPayableException.class);

		verify(paymentEntryRepository, never()).save(any());
	}

	@Test
	void abonoAboveBalance_fails() {
		UUID paymentId = UUID.randomUUID();
		when(paymentRepository.findByIdForUpdate(paymentId))
				.thenReturn(Optional.of(payment(paymentId, "1000.00", PaymentStatus.QUOTED)));
		when(paymentEntryRepository.sumAmountByPaymentId(any())).thenReturn(new BigDecimal("800.00"));

		assertThatThrownBy(() -> useCase.registerEntry(command(paymentId, "300.00")))
				.isInstanceOf(PaymentNotPayableException.class);

		verify(paymentEntryRepository, never()).save(any());
	}

	@Test
	void partialAbono_setsPartialStatus() {
		UUID paymentId = UUID.randomUUID();
		when(paymentRepository.findByIdForUpdate(paymentId))
				.thenReturn(Optional.of(payment(paymentId, "1000.00", PaymentStatus.QUOTED)));
		when(paymentEntryRepository.sumAmountByPaymentId(any())).thenReturn(BigDecimal.ZERO);
		when(paymentEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentResult result = useCase.registerEntry(command(paymentId, "300.00"));

		assertThat(result.status()).isEqualTo(PaymentSnapshotStatus.PARTIAL);
		assertThat(result.amountPaid()).isEqualByComparingTo("300.00");
	}

	@Test
	void abonoCompletingBalance_setsPaidStatus() {
		UUID paymentId = UUID.randomUUID();
		when(paymentRepository.findByIdForUpdate(paymentId))
				.thenReturn(Optional.of(payment(paymentId, "1000.00", PaymentStatus.PARTIAL)));
		when(paymentEntryRepository.sumAmountByPaymentId(any())).thenReturn(new BigDecimal("700.00"));
		when(paymentEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentResult result = useCase.registerEntry(command(paymentId, "300.00"));

		assertThat(result.status()).isEqualTo(PaymentSnapshotStatus.PAID);
		assertThat(result.amountPaid()).isEqualByComparingTo("1000.00");
	}

	private RegisterEntryCommand command(UUID paymentId, String amount) {
		return new RegisterEntryCommand(paymentId, new BigDecimal(amount), PaymentEntryMethod.CASH,
				PaymentChannel.CASHIER, null, null, null, null, null, true, collectorId, "Finanzas", null);
	}

	private Payment payment(UUID paymentId, String amountDue, PaymentStatus status) {
		return new Payment(personId, null, "TUITION", PaymentConceptType.PERIODIC_QUOTA, null, null, programId, null,
				new BigDecimal(amountDue), new BigDecimal(amountDue), null, status, PaymentOrigin.CASHIER, null,
				Instant.parse("2026-10-01T00:00:00Z"), collectorId);
	}
}
