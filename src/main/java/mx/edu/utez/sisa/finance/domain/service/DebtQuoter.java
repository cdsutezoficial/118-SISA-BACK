package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.port.in.QuoteDebtUseCase.DebtQuote;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptRateQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.shared.exception.ConceptNotFoundException;
import mx.edu.utez.sisa.finance.shared.exception.NoRateAvailableException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The one place a charge is priced: the concept's rate in force for the
 * charge's program, minus the discount in force for that charge.
 *
 * <p>Every reader — the live quote, the freeze at checkout, caja — goes
 * through here, so the number a payer is shown and the number they are
 * charged cannot be computed two different ways.
 *
 * <p>Discount lookup, most specific first:
 * <ol>
 * <li>discounts granted on this very charge ({@code payment_benefit.payment_id});</li>
 * <li>otherwise, discounts granted for the concept without a charge (e.g. a
 * scholarship registered before the charge exists), matched by
 * {@code (anchor, conceptCode)} and not tied to any other charge.</li>
 * </ol>
 * Among the candidates of a level, {@link BenefitResolver} picks the one that
 * applies on the date (vigencia, period/generation) — the most recent wins.
 */
public class DebtQuoter {

	private final ConceptQueryPort conceptQueryPort;

	private final ConceptRateQueryPort conceptRateQueryPort;

	private final PaymentBenefitRepository paymentBenefitRepository;

	private final BenefitResolver benefitResolver;

	private final PaymentQuoter paymentQuoter;

	public DebtQuoter(ConceptQueryPort conceptQueryPort, ConceptRateQueryPort conceptRateQueryPort,
			PaymentBenefitRepository paymentBenefitRepository, BenefitResolver benefitResolver,
			PaymentQuoter paymentQuoter) {
		this.conceptQueryPort = conceptQueryPort;
		this.conceptRateQueryPort = conceptRateQueryPort;
		this.paymentBenefitRepository = paymentBenefitRepository;
		this.benefitResolver = benefitResolver;
		this.paymentQuoter = paymentQuoter;
	}

	/**
	 * Prices the charge as of {@code onDate}, from the catalog and the
	 * discounts in force. Never reads the amounts stored on the charge.
	 *
	 * @throws ConceptNotFoundException  if the charge's concept is not in the catalog
	 * @throws NoRateAvailableException  if the concept has no rate for the program
	 */
	public DebtQuote live(Payment payment, LocalDate onDate) {
		ConceptQueryPort.ConceptView concept = conceptQueryPort.findByCode(payment.getConceptCode())
				.orElseThrow(() -> new ConceptNotFoundException(
						"Concept not found for charge " + payment.getId() + ": " + payment.getConceptCode()));
		ConceptRateQueryPort.ActiveRate rate = conceptRateQueryPort
				.findActiveRate(concept.id(), payment.getProgramId())
				.orElseThrow(() -> new NoRateAvailableException("No active rate for concept=" + concept.code()
						+ " program=" + payment.getProgramId()));
		PaymentBenefit benefit = resolveBenefit(payment, onDate).orElse(null);
		PaymentQuoter.Quotation quotation = paymentQuoter.quote(rate.amount(), benefit);
		return new DebtQuote(payment.getId(), payment.getCandidateId(), payment.getStudentId(),
				payment.getConceptCode(), concept.name(), payment.getStatus(), rate.id(), quotation.original(),
				quotation.discount(), quotation.due(), payment.getAmountPaid(),
				BenefitSummaries.from(quotation.benefit()), quotation.exempt(), true, payment.getDueAt(), null);
	}

	/**
	 * The numbers frozen on a charge, as a quote. Used for charges that are no
	 * longer open, where the agreed price must not move.
	 */
	public DebtQuote frozen(Payment payment, String conceptName) {
		BigDecimal original = payment.getOriginalAmount();
		BigDecimal due = payment.getAmountDue();
		BigDecimal discount = original == null || due == null ? null : original.subtract(due);
		PaymentBenefit benefit = payment.getBenefitId() == null ? null
				: paymentBenefitRepository.findById(payment.getBenefitId()).orElse(null);
		boolean exempt = due != null && due.signum() == 0;
		return new DebtQuote(payment.getId(), payment.getCandidateId(), payment.getStudentId(),
				payment.getConceptCode(), conceptName, payment.getStatus(), payment.getRateId(), original, discount,
				due, payment.getAmountPaid(), BenefitSummaries.from(benefit), exempt, false, payment.getDueAt(),
				null);
	}

	/** The concept's display name, or the code when the catalog no longer has it. */
	public String conceptName(Payment payment) {
		return conceptQueryPort.findByCode(payment.getConceptCode()).map(ConceptQueryPort.ConceptView::name)
				.orElse(payment.getConceptCode());
	}

	private Optional<PaymentBenefit> resolveBenefit(Payment payment, LocalDate onDate) {
		Optional<PaymentBenefit> linked = benefitResolver.resolve(
				paymentBenefitRepository.findActiveByPaymentId(payment.getId()), onDate, payment.getPeriodId(),
				payment.getGenerationId());
		if (linked.isPresent()) {
			return linked;
		}
		List<PaymentBenefit> byConcept = (payment.getCandidateId() != null
				? paymentBenefitRepository.findActiveByCandidateAndConcept(payment.getCandidateId(),
						payment.getConceptCode())
				: paymentBenefitRepository.findActiveByStudentAndConcept(payment.getStudentId(),
						payment.getConceptCode()))
				.stream().filter(benefit -> benefit.getPaymentId() == null).toList();
		return benefitResolver.resolve(byConcept, onDate, payment.getPeriodId(), payment.getGenerationId());
	}
}
