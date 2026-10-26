package mx.edu.utez.sisa.finance.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptQueryPort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * JPA-backed {@link ConceptQueryPort} adapter reading
 * {@code academic_config.PaymentConcept}. The availability window is applied
 * here in Java ({@code null} boundaries open on that side) rather than in SQL,
 * so the "open window" rule reads the same as the entity's own fields.
 */
@Component
public class ConceptQueryAdapter implements ConceptQueryPort {

	private final FinanceConceptJpaRepository jpaRepository;

	public ConceptQueryAdapter(FinanceConceptJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public Optional<ConceptView> findSellableByCode(String code, LocalDate onDate) {
		return jpaRepository.findByCodeIgnoreCaseAndStatus(code, PaymentConceptStatus.ACTIVE)
				.filter(concept -> isWithinWindow(concept, onDate))
				.map(ConceptQueryAdapter::toView);
	}

	@Override
	public Optional<ConceptView> findByCode(String code) {
		return jpaRepository.findByCodeIgnoreCase(code).map(ConceptQueryAdapter::toView);
	}

	private static boolean isWithinWindow(PaymentConcept concept, LocalDate onDate) {
		if (concept.getAvailableFrom() != null && onDate.isBefore(concept.getAvailableFrom())) {
			return false;
		}
		return concept.getAvailableUntil() == null || !onDate.isAfter(concept.getAvailableUntil());
	}

	private static ConceptView toView(PaymentConcept concept) {
		return new ConceptView(concept.getId(), concept.getName(), concept.getCode(), concept.getType());
	}
}
