package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.finance.domain.model.BenefitMode;
import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.model.PaymentCredit;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntry;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntryMethod;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.ChangeChargeProgramUseCase.RebillCommand;
import mx.edu.utez.sisa.finance.domain.port.in.ChangeChargeProgramUseCase.RebillOutcome;
import mx.edu.utez.sisa.finance.domain.port.in.QuoteDebtUseCase.DebtQuote;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptRateQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentCreditRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentEntryRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotPayableException;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Cambio de carrera sobre el cargo de la ficha: sin pagar se mueve el mismo
 * cargo con su descuento; pagada se cobra la carrera nueva abonando lo pagado.
 */
@ExtendWith(MockitoExtension.class)
class ChangeChargeProgramUseCaseImplTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T18:00:00Z"),
			ZoneId.of("America/Mexico_City"));

	private static final UUID CANDIDATE_ID = UUID.randomUUID();
	private static final UUID OLD_PROGRAM = UUID.randomUUID();
	private static final UUID NEW_PROGRAM = UUID.randomUUID();
	private static final UUID OLD_CONCEPT_ID = UUID.randomUUID();
	private static final UUID NEW_CONCEPT_ID = UUID.randomUUID();
	private static final UUID NEW_RATE_ID = UUID.randomUUID();

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private PaymentEntryRepository paymentEntryRepository;

	@Mock
	private PaymentCreditRepository paymentCreditRepository;

	@Mock
	private PaymentBenefitRepository paymentBenefitRepository;

	@Mock
	private ConceptQueryPort conceptQueryPort;

	@Mock
	private ConceptRateQueryPort conceptRateQueryPort;

	private ChangeChargeProgramUseCaseImpl useCase;

	private final Map<UUID, Payment> payments = new HashMap<>();

	private final List<PaymentCredit> credits = new ArrayList<>();

	private final List<PaymentEntry> entries = new ArrayList<>();

	private final List<PaymentBenefit> benefits = new ArrayList<>();

	@BeforeEach
	void setUp() {
		useCase = new ChangeChargeProgramUseCaseImpl(paymentRepository, paymentEntryRepository,
				paymentCreditRepository, paymentBenefitRepository, conceptQueryPort, conceptRateQueryPort,
				new BenefitResolver(), new PaymentQuoter(), new PaymentSettlementDecider(), CLOCK);

		lenient().when(conceptQueryPort.findByCode("FICHA_ING")).thenReturn(Optional.of(
				new ConceptQueryPort.ConceptView(OLD_CONCEPT_ID, "Ficha", "FICHA_ING", PaymentConceptType.ADMISSION)));
		lenient().when(conceptQueryPort.findByCode("FICHA_LIC")).thenReturn(Optional.of(
				new ConceptQueryPort.ConceptView(NEW_CONCEPT_ID, "Ficha", "FICHA_LIC", PaymentConceptType.ADMISSION)));

		lenient().when(paymentRepository.save(any())).thenAnswer(invocation -> {
			Payment payment = invocation.getArgument(0);
			if (payment.getId() == null) {
				ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
			}
			payments.put(payment.getId(), payment);
			return payment;
		});
		lenient().when(paymentRepository.findById(any()))
				.thenAnswer(invocation -> Optional.ofNullable(payments.get(invocation.getArgument(0))));
		lenient().when(paymentRepository.findByIdForUpdate(any()))
				.thenAnswer(invocation -> Optional.ofNullable(payments.get(invocation.getArgument(0))));

		lenient().when(paymentCreditRepository.save(any())).thenAnswer(invocation -> {
			PaymentCredit credit = invocation.getArgument(0);
			if (credit.getId() == null) {
				ReflectionTestUtils.setField(credit, "id", UUID.randomUUID());
				credits.add(credit);
			}
			return credit;
		});
		lenient().when(paymentCreditRepository.findAvailableByCandidateId(CANDIDATE_ID))
				.thenAnswer(invocation -> credits.stream().filter(PaymentCredit::isAvailable).toList());
		lenient().when(paymentCreditRepository.findByIdForUpdate(any())).thenAnswer(invocation -> credits.stream()
				.filter(credit -> credit.getId().equals(invocation.getArgument(0))).findFirst());

		lenient().when(paymentEntryRepository.save(any())).thenAnswer(invocation -> {
			entries.add(invocation.getArgument(0));
			return invocation.getArgument(0);
		});
		lenient().when(paymentEntryRepository.sumAmountByPaymentId(any())).thenAnswer(invocation -> entries.stream()
				.filter(entry -> entry.getPaymentId().equals(invocation.getArgument(0))).map(PaymentEntry::getAmount)
				.reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add));

		lenient().when(paymentBenefitRepository.findActiveByPaymentId(any())).thenAnswer(invocation -> benefits
				.stream().filter(benefit -> invocation.getArgument(0).equals(benefit.getPaymentId())).toList());
		lenient().when(paymentBenefitRepository.findActiveByCandidateAndConcept(any(), any()))
				.thenAnswer(invocation -> benefits.stream()
						.filter(benefit -> benefit.getConceptCode().equals(invocation.getArgument(1))).toList());
	}

	// ── Sin pagar ────────────────────────────────────────────────────────────

	@Test
	void unpaidChargeMovesToTheNewProgramAndKeepsItsDiscount() {
		rate(NEW_CONCEPT_ID, NEW_PROGRAM, "1650.00");
		Payment ficha = header();
		// Descuento del 100 % otorgado al concepto, sin cargo: tiene que seguir al cargo.
		benefits.add(benefit(null, "FICHA_ING", BenefitMode.FULL, null));

		DebtQuote preview = useCase.previewReassign(ficha.getId(), "FICHA_LIC", NEW_PROGRAM);
		useCase.reassign(ficha.getId(), "FICHA_LIC", NEW_PROGRAM);

		assertThat(preview.originalAmount()).isEqualByComparingTo("1650.00");
		assertThat(preview.amountDue()).isEqualByComparingTo("0.00");
		assertThat(preview.exempt()).isTrue();
		assertThat(ficha.getConceptCode()).isEqualTo("FICHA_LIC");
		assertThat(ficha.getProgramId()).isEqualTo(NEW_PROGRAM);
		assertThat(ficha.isPriced()).isFalse();
		assertThat(benefits.get(0).getPaymentId()).isEqualTo(ficha.getId());
	}

	@Test
	void unpaidChargeKeepsAPartialDiscountOnTheNewTariff() {
		rate(NEW_CONCEPT_ID, NEW_PROGRAM, "1650.00");
		Payment ficha = header();
		benefits.add(benefit(ficha.getId(), "FICHA_ING", BenefitMode.PERCENT, new BigDecimal("50")));

		DebtQuote preview = useCase.previewReassign(ficha.getId(), "FICHA_LIC", NEW_PROGRAM);

		assertThat(preview.amountDue()).isEqualByComparingTo("825.00");
	}

	@Test
	void aFrozenUnpaidChargeGoesBackToBeingAHeader() {
		rate(NEW_CONCEPT_ID, NEW_PROGRAM, "1650.00");
		Payment ficha = header();
		ficha.reprice(new BigDecimal("1120.00"), new BigDecimal("1120.00"), null, UUID.randomUUID(),
				PaymentStatus.PENDING);

		useCase.reassign(ficha.getId(), "FICHA_LIC", NEW_PROGRAM);

		assertThat(ficha.getOriginalAmount()).isNull();
		assertThat(ficha.getAmountDue()).isNull();
	}

	@Test
	void aPaidChargeCannotBeReassigned() {
		rate(NEW_CONCEPT_ID, NEW_PROGRAM, "1650.00");
		Payment ficha = paid("1420.00", "1420.00");

		assertThatThrownBy(() -> useCase.reassign(ficha.getId(), "FICHA_LIC", NEW_PROGRAM))
				.isInstanceOf(PaymentNotPayableException.class);
	}

	// ── Pagada ───────────────────────────────────────────────────────────────

	@Test
	void paidAndMoreExpensiveLeavesTheDifferenceToPay() {
		rate(NEW_CONCEPT_ID, NEW_PROGRAM, "1750.00");
		Payment ficha = paid("1420.00", "1420.00");

		RebillOutcome outcome = useCase.rebill(command(ficha.getId()));

		assertThat(outcome.alreadyPaid()).isEqualByComparingTo("1420.00");
		assertThat(outcome.toPay()).isEqualByComparingTo("330.00");
		assertThat(outcome.creditLeft()).isEqualByComparingTo("0.00");
		assertThat(outcome.status()).isEqualTo(PaymentStatus.PARTIAL);
		Payment charge = payments.get(outcome.paymentId());
		assertThat(charge.getOriginalAmount()).isEqualByComparingTo("1750.00");
		assertThat(charge.getAmountPaid()).isEqualByComparingTo("1420.00");
		assertThat(charge.isPriceLocked()).isTrue();
		assertThat(charge.isRepriceable()).isFalse();
		assertThat(charge.getReferenceNumber()).isEqualTo("REF-ADM-2026-000001-C1");
		// El cargo pagado queda como historial, intacto.
		assertThat(ficha.getStatus()).isEqualTo(PaymentStatus.PAID);
		assertThat(ficha.getProgramId()).isEqualTo(OLD_PROGRAM);
		assertThat(entries).singleElement().satisfies(entry -> {
			assertThat(entry.getMethod()).isEqualTo(PaymentEntryMethod.CREDIT);
			assertThat(entry.getAmount()).isEqualByComparingTo("1420.00");
		});
	}

	@Test
	void paidAndCheaperLeavesSaldoAFavor() {
		rate(NEW_CONCEPT_ID, NEW_PROGRAM, "1400.00");
		Payment ficha = paid("1800.00", "1800.00");

		RebillOutcome outcome = useCase.rebill(command(ficha.getId()));

		assertThat(outcome.toPay()).isEqualByComparingTo("0.00");
		assertThat(outcome.creditLeft()).isEqualByComparingTo("400.00");
		assertThat(outcome.status()).isEqualTo(PaymentStatus.PAID);
		assertThat(credits).singleElement().satisfies(credit -> {
			assertThat(credit.isAvailable()).isTrue();
			assertThat(credit.getAmount()).isEqualByComparingTo("400.00");
			assertThat(credit.getOriginPaymentId()).isEqualTo(ficha.getId());
		});
	}

	@Test
	void paidWithAFullDiscountPaysTheWholeNewTariff() {
		rate(NEW_CONCEPT_ID, NEW_PROGRAM, "1650.00");
		// Pagó $0 con el 100 %: el descuento ya se usó y no pasa a la carrera nueva.
		Payment ficha = paid("1120.00", "0.00");
		benefits.add(benefit(ficha.getId(), "FICHA_ING", BenefitMode.FULL, null));

		RebillOutcome outcome = useCase.rebill(command(ficha.getId()));

		assertThat(outcome.alreadyPaid()).isEqualByComparingTo("0.00");
		assertThat(outcome.toPay()).isEqualByComparingTo("1650.00");
		assertThat(outcome.status()).isEqualTo(PaymentStatus.PENDING);
		assertThat(credits).isEmpty();
		assertThat(payments.get(outcome.paymentId()).getBenefitId()).isNull();
	}

	@Test
	void sameTariffLeavesNothingToPayNorCredit() {
		rate(NEW_CONCEPT_ID, NEW_PROGRAM, "1420.00");
		Payment ficha = paid("1420.00", "1420.00");

		RebillOutcome outcome = useCase.rebill(command(ficha.getId()));

		assertThat(outcome.toPay()).isEqualByComparingTo("0.00");
		assertThat(outcome.creditLeft()).isEqualByComparingTo("0.00");
		assertThat(outcome.status()).isEqualTo(PaymentStatus.PAID);
	}

	@Test
	void changingAgainReplacesTheUnpaidDifferenceInsteadOfDuplicatingIt() {
		rate(NEW_CONCEPT_ID, NEW_PROGRAM, "1750.00");
		rate(OLD_CONCEPT_ID, OLD_PROGRAM, "1420.00");
		Payment ficha = paid("1420.00", "1420.00");
		RebillOutcome first = useCase.rebill(command(ficha.getId()));

		// Regresa a la carrera original: lo abonado vuelve como saldo a favor y la
		// diferencia anterior se cancela; no queda nada por pagar.
		RebillOutcome back = useCase.rebill(new RebillCommand(first.paymentId(), "FICHA_ING", OLD_PROGRAM,
				Instant.parse("2026-11-07T05:59:59Z"), "REF-ADM-2026-000001-C2", UUID.randomUUID()));

		assertThat(payments.get(first.paymentId()).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
		assertThat(back.alreadyPaid()).isEqualByComparingTo("1420.00");
		assertThat(back.toPay()).isEqualByComparingTo("0.00");
		assertThat(back.status()).isEqualTo(PaymentStatus.PAID);
		assertThat(credits.stream().filter(PaymentCredit::isAvailable)).isEmpty();
	}

	@Test
	void previewMatchesWhatRebillDoesWithoutWriting() {
		rate(NEW_CONCEPT_ID, NEW_PROGRAM, "1750.00");
		Payment ficha = paid("1420.00", "1420.00");

		RebillOutcome preview = useCase.previewRebill(ficha.getId(), "FICHA_LIC", NEW_PROGRAM);

		assertThat(preview.paymentId()).isNull();
		assertThat(preview.toPay()).isEqualByComparingTo("330.00");
		assertThat(payments).hasSize(1);
		assertThat(credits).isEmpty();
	}

	private RebillCommand command(UUID fromPaymentId) {
		return new RebillCommand(fromPaymentId, "FICHA_LIC", NEW_PROGRAM, Instant.parse("2026-11-07T05:59:59Z"),
				"REF-ADM-2026-000001-C1", UUID.randomUUID());
	}

	private void rate(UUID conceptId, UUID programId, String amount) {
		lenient().when(conceptRateQueryPort.findActiveRate(conceptId, programId)).thenReturn(Optional.of(
				new ConceptRateQueryPort.ActiveRate(conceptId.equals(NEW_CONCEPT_ID) ? NEW_RATE_ID : UUID.randomUUID(),
						new BigDecimal(amount))));
	}

	private Payment header() {
		Payment payment = new Payment(CANDIDATE_ID, null, "FICHA_ING", null, null, OLD_PROGRAM, null, null, null,
				PaymentStatus.PENDING, PaymentOrigin.ADMISSION, Instant.parse("2026-11-07T05:59:59Z"),
				Instant.parse("2026-10-20T00:00:00Z"));
		payment.assignReference("REF-ADM-2026-000001");
		return paymentRepository.save(payment);
	}

	private Payment paid(String tariff, String paidAmount) {
		BigDecimal paidValue = new BigDecimal(paidAmount);
		Payment payment = new Payment(CANDIDATE_ID, null, "FICHA_ING", null, null, OLD_PROGRAM,
				new BigDecimal(tariff), paidValue, null, PaymentStatus.PENDING, PaymentOrigin.ADMISSION,
				Instant.parse("2026-11-07T05:59:59Z"), Instant.parse("2026-10-20T00:00:00Z"));
		payment.assignReference("REF-ADM-2026-000001");
		payment.settle(paidValue, paidValue.signum() == 0 ? PaymentStatus.EXEMPT : PaymentStatus.PAID);
		return paymentRepository.save(payment);
	}

	private PaymentBenefit benefit(UUID paymentId, String conceptCode, BenefitMode mode, BigDecimal percent) {
		PaymentBenefit benefit = new PaymentBenefit(paymentId, CANDIDATE_ID, null, conceptCode, null, null, mode,
				percent, null, LocalDate.of(2026, 1, 1), null, "Beca", UUID.randomUUID(), "SE",
				Instant.parse("2026-10-01T00:00:00Z"));
		ReflectionTestUtils.setField(benefit, "id", UUID.randomUUID());
		return benefit;
	}
}
