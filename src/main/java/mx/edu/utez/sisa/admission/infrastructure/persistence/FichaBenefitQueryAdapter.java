package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.port.out.FichaBenefitQueryPort;
import mx.edu.utez.sisa.finance.domain.port.in.QuoteBenefitUseCase;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Adapter implementing {@link FichaBenefitQueryPort} by delegating to
 * {@code finance}'s {@link QuoteBenefitUseCase}. This is the one seam where the
 * admission module reaches into the discount engine; the finance domain types
 * do not leak past this class.
 */
@Component
public class FichaBenefitQueryAdapter implements FichaBenefitQueryPort {

	private final QuoteBenefitUseCase quoteBenefitUseCase;

	public FichaBenefitQueryAdapter(QuoteBenefitUseCase quoteBenefitUseCase) {
		this.quoteBenefitUseCase = quoteBenefitUseCase;
	}

	@Override
	public FichaDiscount resolve(UUID candidateId, String conceptCode, BigDecimal rateAmount, LocalDate onDate) {
		if (candidateId == null) {
			return FichaDiscount.none(rateAmount);
		}
		QuoteBenefitUseCase.BenefitQuote quote = quoteBenefitUseCase.quote(new QuoteBenefitUseCase.BenefitQuoteCommand(
				candidateId, null, conceptCode, null, null, rateAmount, onDate));
		return new FichaDiscount(quote.originalAmount(), quote.discountAmount(), quote.amountDue(),
				quote.benefitId(), quote.exempt());
	}
}
