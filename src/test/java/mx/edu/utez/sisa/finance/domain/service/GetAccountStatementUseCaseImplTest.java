package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntry;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntryMethod;
import mx.edu.utez.sisa.finance.domain.model.PaymentChannel;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.GetAccountStatementUseCase.AccountStatement;
import mx.edu.utez.sisa.finance.domain.port.in.GetAccountStatementUseCase.StatementPayment;
import mx.edu.utez.sisa.finance.domain.port.out.AnchorQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentEntryRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PersonQueryPort.PersonView;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotFoundException;
import mx.edu.utez.sisa.finance.shared.exception.PersonNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAccountStatementUseCaseImplTest {

	@Mock
	private AnchorQueryPort anchorQueryPort;

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private PaymentEntryRepository paymentEntryRepository;

	@Mock
	private PaymentBenefitRepository paymentBenefitRepository;

	private GetAccountStatementUseCaseImpl useCase;

	private UUID candidateId;

	@BeforeEach
	void setUp() {
		useCase = new GetAccountStatementUseCaseImpl(anchorQueryPort, paymentRepository, paymentEntryRepository,
				paymentBenefitRepository);
		candidateId = UUID.randomUUID();
	}

	@Test
	void unknownAnchor_fails() {
		when(anchorQueryPort.findPersonByCandidateId(candidateId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.getAccountStatement(candidateId, null))
				.isInstanceOf(PersonNotFoundException.class);
	}

	@Test
	void statement_aggregatesPaymentsWithEntriesAndBalance() {
		Payment payment = payment("1000.00", "300.00", PaymentStatus.PARTIAL);
		PaymentEntry entry = new PaymentEntry(null, new BigDecimal("300.00"), PaymentEntryMethod.CASH,
				PaymentChannel.CASHIER, null, null, null, null, "REC-1", true, UUID.randomUUID(), "Finanzas",
				Instant.parse("2026-10-10T00:00:00Z"), null);
		when(anchorQueryPort.findPersonByCandidateId(candidateId)).thenReturn(Optional.of(
				new PersonView(candidateId, "CURP", "Ana", "Lopez", "Perez")));
		when(paymentRepository.findByCandidateId(candidateId)).thenReturn(List.of(payment));
		when(paymentEntryRepository.findByPaymentIdOrderByPaidAtAsc(any())).thenReturn(List.of(entry));

		AccountStatement statement = useCase.getAccountStatement(candidateId, null);

		assertThat(statement.personName()).isEqualTo("Ana Lopez Perez");
		assertThat(statement.payments()).hasSize(1);
		StatementPayment row = statement.payments().get(0);
		assertThat(row.balance()).isEqualByComparingTo("700.00");
		assertThat(row.entries()).hasSize(1);
		assertThat(row.entries().get(0).receiptNumber()).isEqualTo("REC-1");
	}

	@Test
	void getPaymentUnknown_fails() {
		UUID paymentId = UUID.randomUUID();
		when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.getPayment(paymentId))
				.isInstanceOf(PaymentNotFoundException.class);
	}

	@Test
	void getPayment_foundReturnsBalance() {
		UUID paymentId = UUID.randomUUID();
		Payment payment = payment("1000.00", "1000.00", PaymentStatus.PAID);
		when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
		when(paymentEntryRepository.findByPaymentIdOrderByPaidAtAsc(any())).thenReturn(List.of());

		StatementPayment row = useCase.getPayment(paymentId);

		assertThat(row.balance()).isEqualByComparingTo("0.00");
		assertThat(row.status()).isEqualTo(PaymentStatus.PAID);
	}

	private Payment payment(String amountDue, String amountPaid, PaymentStatus status) {
		Payment payment = new Payment(candidateId, null, "TUITION", null, null, UUID.randomUUID(),
				new BigDecimal(amountDue), new BigDecimal(amountDue), null, status, PaymentOrigin.CASHIER, null,
				Instant.parse("2026-10-01T00:00:00Z"));
		payment.settle(new BigDecimal(amountPaid), status);
		return payment;
	}
}
