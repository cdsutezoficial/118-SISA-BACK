package mx.edu.utez.sisa.finance.domain.port.in;

import mx.edu.utez.sisa.finance.domain.model.BenefitStatus;

import java.util.List;
import java.util.UUID;

/**
 * Lists discounts by candidate/student/concept/status (Fase 2 §5.1), newest
 * first. Every criterion is optional ({@code null} = "any"), so the same use
 * case backs the manager filter and a plain "todo" listing.
 */
public interface ListBenefitsUseCase {

	List<BenefitView> listBenefits(BenefitFilter filter);

	record BenefitFilter(UUID candidateId, UUID studentId, String conceptCode, BenefitStatus status) {
	}
}
