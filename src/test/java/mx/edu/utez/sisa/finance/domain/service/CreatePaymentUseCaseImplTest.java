package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.finance.domain.model.BenefitMode;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.finance.domain.port.in.CreatePaymentUseCase.CreatePaymentCommand;
import mx.edu.utez.sisa.finance.domain.port.in.CreatePaymentUseCase.PaymentResult;
import mx.edu.utez.sisa.finance.domain.port.in.CreatePaymentUseCase.PaymentSnapshotStatus;
import mx.edu.utez.sisa.finance.domain.port.in.QuotePaymentUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.QuotePaymentUseCase.BenefitSummary;
import mx.edu.utez.sisa.finance.domain.port.in.QuotePaymentUseCase.QuoteResult;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.InvalidPaymentDataException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatePaymentUseCaseImplTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"),
			ZoneId.of("America/Mexico_City"));

	@Mock
	private QuotePaymentUseCase quotePaymentUseCase;

	@Mock
	private PaymentRepository paymentRepository;

	private CreatePaymentUseCaseImpl useCase;

	private UUID candidateId;
	private UUID programId;

	@BeforeEach
	void setUp() {
		useCase = new CreatePaymentUseCaseImpl(quotePaymentUseCase, paymentRepository, CLOCK);
		candidateId = UUID.randomUUID();
		programId = UUID.randomUUID();
	}

	@Test
	void missingOrigin_failsValidation() {
		assertThatThrownBy(() -> useCase.createPayment(command(null)))
				.isInstanceOf(InvalidPaymentDataException.class);

		verify(paymentRepository, never()).save(any());
	}

	@Test
	void createsQuotedPaymentFreezingTheQuote() {
		when(quotePaymentUseCase.quote(any())).thenReturn(new QuoteResult(PaymentConceptType.PERIODIC_QUOTA,
				new BigDecimal("1000.00"), new BigDecimal("250.00"), new BigDecimal("750.00"), null, false));
		when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentResult result = useCase.createPayment(command(PaymentOrigin.CASHIER));

		assertThat(result.status()).isEqualTo(PaymentSnapshotStatus.PENDING);
		assertThat(result.originalAmount()).isEqualByComparingTo("1000.00");
		assertThat(result.amountDue()).isEqualByComparingTo("750.00");
		assertThat(result.amountPaid()).isEqualByComparingTo("0.00");
		assertThat(result.benefit()).isNull();
	}

	@Test
	void createsExemptPaymentForFullBenefit() {
		BenefitSummary summary = new BenefitSummary(UUID.randomUUID(), BenefitMode.FULL, null, null, "exención",
				"Finanzas");
		when(quotePaymentUseCase.quote(any())).thenReturn(new QuoteResult(PaymentConceptType.PERIODIC_QUOTA,
				new BigDecimal("1000.00"), new BigDecimal("1000.00"), new BigDecimal("0.00"), summary, true));
		when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentResult result = useCase.createPayment(command(PaymentOrigin.CASHIER));

		assertThat(result.status()).isEqualTo(PaymentSnapshotStatus.EXEMPT);
		assertThat(result.amountDue()).isEqualByComparingTo("0.00");
		assertThat(result.benefit()).isEqualTo(summary);
	}

	private CreatePaymentCommand command(PaymentOrigin origin) {
		return new CreatePaymentCommand(candidateId, null, "TUITION", null, null, programId, null, origin, null, null,
				UUID.randomUUID());
	}
}
