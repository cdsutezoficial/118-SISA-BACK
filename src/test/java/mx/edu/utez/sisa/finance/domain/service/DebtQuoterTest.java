package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.finance.domain.model.BenefitMode;
import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.QuoteDebtUseCase.DebtQuote;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptQueryPort.ConceptView;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptRateQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.shared.exception.NoRateAvailableException;
import org.junit.jupiter.api.BeforeEach;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The single pricing rule of a charge: the concept's rate in force for its
 * program, minus the discount in force for that charge — computed live, never
 * read from the amounts stored on the row.
 */
@ExtendWith(MockitoExtension.class)
class DebtQuoterTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

	@Mock
	private ConceptQueryPort conceptQueryPort;

	@Mock
	private ConceptRateQueryPort conceptRateQueryPort;

	@Mock
	private PaymentBenefitRepository paymentBenefitRepository;

	private DebtQuoter quoter;

	private UUID candidateId;
	private UUID programId;
	private UUID rateId;
	private ConceptView concept;
	private Payment charge;

	@BeforeEach
	void setUp() {
		quoter = new DebtQuoter(conceptQueryPort, conceptRateQueryPort, paymentBenefitRepository,
				new BenefitResolver(), new PaymentQuoter());
		candidateId = UUID.randomUUID();
		programId = UUID.randomUUID();
		rateId = UUID.randomUUID();
		concept = new ConceptView(UUID.randomUUID(), "Ficha de admisión Ingeniería", "FICHA-ING",
				PaymentConceptType.ADMISSION);
		charge = header();
		when(conceptQueryPort.findByCode("FICHA-ING")).thenReturn(Optional.of(concept));
		lenient().when(conceptRateQueryPort.findActiveRate(concept.id(), programId))
				.thenReturn(Optional.of(new ConceptRateQueryPort.ActiveRate(rateId, new BigDecimal("1120.00"))));
		lenient().when(paymentBenefitRepository.findActiveByPaymentId(charge.getId())).thenReturn(List.of());
		lenient().when(paymentBenefitRepository.findActiveByCandidateAndConcept(candidateId, "FICHA-ING"))
				.thenReturn(List.of());
	}

	private Payment header() {
		Payment payment = new Payment(candidateId, null, "FICHA-ING", null, null, programId, null, null, null,
				PaymentStatus.PENDING, PaymentOrigin.ADMISSION, null, Instant.parse("2026-10-01T00:00:00Z"));
		ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
		return payment;
	}

	private PaymentBenefit percent(UUID paymentId, String pct, Instant createdAt) {
		PaymentBenefit benefit = new PaymentBenefit(paymentId, candidateId, null, "FICHA-ING", null, null,
				BenefitMode.PERCENT, new BigDecimal(pct), null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31),
				"promedio", UUID.randomUUID(), "Servicios Escolares", createdAt);
		ReflectionTestUtils.setField(benefit, "id", UUID.randomUUID());
		return benefit;
	}

	@Test
	void withoutDiscount_chargesTheConceptsRateInForce() {
		DebtQuote quote = quoter.live(charge, TODAY);

		assertThat(quote.originalAmount()).isEqualByComparingTo("1120.00");
		assertThat(quote.discountAmount()).isEqualByComparingTo("0.00");
		assertThat(quote.amountDue()).isEqualByComparingTo("1120.00");
		assertThat(quote.rateId()).isEqualTo(rateId);
		assertThat(quote.live()).isTrue();
		assertThat(quote.benefit()).isNull();
	}

	@Test
	void aDiscountGrantedOnTheChargeIsApplied() {
		PaymentBenefit onCharge = percent(charge.getId(), "25", Instant.parse("2026-10-10T00:00:00Z"));
		when(paymentBenefitRepository.findActiveByPaymentId(charge.getId())).thenReturn(List.of(onCharge));

		DebtQuote quote = quoter.live(charge, TODAY);

		assertThat(quote.discountAmount()).isEqualByComparingTo("280.00");
		assertThat(quote.amountDue()).isEqualByComparingTo("840.00");
		assertThat(quote.benefitId()).isEqualTo(onCharge.getId());
	}

	/** The rate moved after registration: the live quote follows the catalog, not the row. */
	@Test
	void theRowsStoredAmountsAreNeverRead() {
		ReflectionTestUtils.setField(charge, "originalAmount", new BigDecimal("900.00"));
		ReflectionTestUtils.setField(charge, "amountDue", new BigDecimal("900.00"));

		assertThat(quoter.live(charge, TODAY).amountDue()).isEqualByComparingTo("1120.00");
	}

	@Test
	void aLegacyDiscountTiedToAnotherChargeIsIgnored() {
		PaymentBenefit otherCharge = percent(UUID.randomUUID(), "50", Instant.parse("2026-10-10T00:00:00Z"));
		PaymentBenefit legacy = percent(null, "10", Instant.parse("2026-10-05T00:00:00Z"));
		when(paymentBenefitRepository.findActiveByCandidateAndConcept(candidateId, "FICHA-ING"))
				.thenReturn(List.of(otherCharge, legacy));

		DebtQuote quote = quoter.live(charge, TODAY);

		assertThat(quote.benefitId()).isEqualTo(legacy.getId());
		assertThat(quote.amountDue()).isEqualByComparingTo("1008.00");
	}

	@Test
	void anExpiredDiscountIsNotApplied() {
		PaymentBenefit onCharge = percent(charge.getId(), "25", Instant.parse("2026-10-10T00:00:00Z"));
		when(paymentBenefitRepository.findActiveByPaymentId(charge.getId())).thenReturn(List.of(onCharge));

		DebtQuote quote = quoter.live(charge, LocalDate.of(2027, 1, 15));

		assertThat(quote.benefit()).isNull();
		assertThat(quote.amountDue()).isEqualByComparingTo("1120.00");
	}

	@Test
	void aFullDiscountQuotesZeroAndExempt() {
		PaymentBenefit full = new PaymentBenefit(charge.getId(), candidateId, null, "FICHA-ING", null, null,
				BenefitMode.FULL, null, null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), "beca",
				UUID.randomUUID(), "Finanzas", Instant.parse("2026-10-10T00:00:00Z"));
		when(paymentBenefitRepository.findActiveByPaymentId(charge.getId())).thenReturn(List.of(full));

		DebtQuote quote = quoter.live(charge, TODAY);

		assertThat(quote.amountDue()).isEqualByComparingTo("0.00");
		assertThat(quote.exempt()).isTrue();
	}

	@Test
	void aConceptWithNoRateForTheProgramFailsLoud() {
		when(conceptRateQueryPort.findActiveRate(any(), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> quoter.live(charge, TODAY)).isInstanceOf(NoRateAvailableException.class);
	}
}
