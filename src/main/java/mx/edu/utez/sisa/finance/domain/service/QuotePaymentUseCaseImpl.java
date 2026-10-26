package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.port.in.QuotePaymentUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.AnchorQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptQueryPort.ConceptView;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptRateQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.shared.exception.ConceptNotFoundException;
import mx.edu.utez.sisa.finance.shared.exception.ConceptUnavailableException;
import mx.edu.utez.sisa.finance.shared.exception.InvalidPaymentDataException;
import mx.edu.utez.sisa.finance.shared.exception.NoRateAvailableException;
import mx.edu.utez.sisa.finance.shared.exception.PersonNotFoundException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Cotizador interactor (Fase 1 §5.3). Resolves the anchor (candidate/student),
 * the sellable concept, the rate (via the ladder), and the discount in force,
 * then hands the numbers to {@link PaymentQuoter}. Reads only — nothing is
 * persisted.
 *
 * <p>Failure order is deliberate: anchor, then concept existence, then window,
 * then rate. Each step's error names the concrete missing piece, so a
 * misconfiguration ("la carrera no tiene tarifa") never surfaces as the vague
 * "concepto no disponible" a single combined check would produce.
 */
public class QuotePaymentUseCaseImpl implements QuotePaymentUseCase {

	private final AnchorQueryPort anchorQueryPort;

	private final ConceptQueryPort conceptQueryPort;

	private final ConceptRateQueryPort conceptRateQueryPort;

	private final PaymentBenefitRepository paymentBenefitRepository;

	private final PaymentQuoter paymentQuoter;

	private final BenefitResolver benefitResolver;

	private final Clock clock;

	public QuotePaymentUseCaseImpl(AnchorQueryPort anchorQueryPort, ConceptQueryPort conceptQueryPort,
			ConceptRateQueryPort conceptRateQueryPort, PaymentBenefitRepository paymentBenefitRepository,
			PaymentQuoter paymentQuoter, BenefitResolver benefitResolver, Clock clock) {
		this.anchorQueryPort = anchorQueryPort;
		this.conceptQueryPort = conceptQueryPort;
		this.conceptRateQueryPort = conceptRateQueryPort;
		this.paymentBenefitRepository = paymentBenefitRepository;
		this.paymentQuoter = paymentQuoter;
		this.benefitResolver = benefitResolver;
		this.clock = clock;
	}

	@Override
	public QuoteResult quote(QuoteCommand command) {
		validate(command);
		LocalDate onDate = command.onDate() == null ? LocalDate.now(clock) : command.onDate();
		boolean candidate = command.candidateId() != null;
		if (candidate ? !anchorQueryPort.candidateExists(command.candidateId())
				: !anchorQueryPort.studentExists(command.studentId())) {
			throw new PersonNotFoundException("Anchor not found: "
					+ (candidate ? command.candidateId() : command.studentId()));
		}
		ConceptView concept = resolveSellableConcept(command.conceptCode(), onDate);
		BigDecimal rate = conceptRateQueryPort
				.findActiveAmount(concept.id(), command.programId(), command.level())
				.orElseThrow(() -> new NoRateAvailableException("No active rate for concept=" + concept.code()
						+ " program=" + command.programId()));
		Optional<PaymentBenefit> benefit = benefitResolver.resolve(candidate
				? paymentBenefitRepository.findActiveByCandidateAndConcept(command.candidateId(), command.conceptCode())
				: paymentBenefitRepository.findActiveByStudentAndConcept(command.studentId(), command.conceptCode()),
				onDate, command.periodId(), command.generationId());
		PaymentQuoter.Quotation quotation = paymentQuoter.quote(rate, benefit.orElse(null));
		return new QuoteResult(concept.type(), quotation.original(), quotation.discount(), quotation.due(),
				BenefitSummaries.from(quotation.benefit()), quotation.exempt());
	}

	private ConceptView resolveSellableConcept(String conceptCode, LocalDate onDate) {
		return conceptQueryPort.findSellableByCode(conceptCode, onDate).orElseThrow(() -> {
			if (conceptQueryPort.findByCode(conceptCode).isPresent()) {
				return new ConceptUnavailableException("Concept is not on sale on " + onDate + ": " + conceptCode);
			}
			return new ConceptNotFoundException("Concept not found: " + conceptCode);
		});
	}

	static void validate(QuoteCommand command) {
		if ((command.candidateId() == null) == (command.studentId() == null)) {
			throw new InvalidPaymentDataException("exactly one of candidateId/studentId is required");
		}
		if (command.conceptCode() == null || command.conceptCode().isBlank()) {
			throw new InvalidPaymentDataException("conceptCode is required");
		}
		if (command.programId() == null) {
			throw new InvalidPaymentDataException("programId is required to resolve the rate");
		}
	}
}
