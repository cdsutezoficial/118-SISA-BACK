package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.CreditStatus;
import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentCredit;
import mx.edu.utez.sisa.finance.domain.port.in.ListOverpaymentsUseCase.OverpaymentView;
import mx.edu.utez.sisa.finance.domain.port.out.AnchorQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentCreditRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PersonQueryPort.PersonView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListOverpaymentsUseCaseImplTest {

	private final PaymentCreditRepository credits = mock(PaymentCreditRepository.class);

	private final PaymentRepository payments = mock(PaymentRepository.class);

	private final AnchorQueryPort anchors = mock(AnchorQueryPort.class);

	private final ListOverpaymentsUseCaseImpl useCase = new ListOverpaymentsUseCaseImpl(credits, payments, anchors);

	@Test
	void namesWhoseMoneyItIsAndTheChargeItCameFrom() {
		UUID candidate = UUID.randomUUID();
		UUID ficha = UUID.randomUUID();
		PaymentCredit credit = PaymentCredit.overpayment(candidate, null, ficha, "ORD-1", new BigDecimal("1750.00"),
				Instant.parse("2026-11-04T08:00:00Z"));
		Payment payment = mock(Payment.class);
		when(payment.getReferenceNumber()).thenReturn("REF-ADM-2026-000010");
		when(credits.findOverpayments()).thenReturn(List.of(credit));
		when(payments.findById(ficha)).thenReturn(Optional.of(payment));
		when(anchors.findPayerByCandidateId(candidate))
				.thenReturn(Optional.of(new PersonView(UUID.randomUUID(), "PEGJ900101HMSRRN09", "Juan", "Pérez", null)));

		List<OverpaymentView> items = useCase.list();

		assertThat(items).singleElement().satisfies(item -> {
			assertThat(item.personName()).isEqualTo("Juan Pérez");
			assertThat(item.curp()).isEqualTo("PEGJ900101HMSRRN09");
			assertThat(item.originReference()).isEqualTo("REF-ADM-2026-000010");
			assertThat(item.appliedToReference()).isNull();
			assertThat(item.externalOrderId()).isEqualTo("ORD-1");
			assertThat(item.status()).isEqualTo(CreditStatus.AVAILABLE);
			assertThat(item.amount()).isEqualByComparingTo("1750.00");
		});
	}

	@Test
	void aCandidateThatNoLongerExistsStillListsTheMoney() {
		UUID candidate = UUID.randomUUID();
		when(credits.findOverpayments()).thenReturn(List.of(PaymentCredit.overpayment(candidate, null, null, "ORD-2",
				new BigDecimal("100.00"), Instant.parse("2026-11-04T08:00:00Z"))));
		when(anchors.findPayerByCandidateId(candidate)).thenReturn(Optional.empty());

		assertThat(useCase.list()).singleElement().satisfies(item -> {
			assertThat(item.personName()).isNull();
			assertThat(item.originReference()).isNull();
		});
	}
}
