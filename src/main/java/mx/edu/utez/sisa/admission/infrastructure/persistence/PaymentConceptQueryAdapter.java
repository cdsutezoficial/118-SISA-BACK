package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.admission.domain.port.out.PaymentConceptQueryPort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * JPA-backed {@link PaymentConceptQueryPort} adapter: finds the {@code ACTIVE}
 * {@code ENROLLMENT} concepts flagged {@code is_tuition} that price the ficha
 * of a program and maps only the minimal projection (concept name + pricing)
 * the admission flow needs. The {@code is_tuition} narrowing lives in
 * {@link PaymentConceptLookupJpaRepository#findActiveTuitionForProgram}.
 */
@Component
public class PaymentConceptQueryAdapter implements PaymentConceptQueryPort {

	private final PaymentConceptLookupJpaRepository lookupJpaRepository;

	public PaymentConceptQueryAdapter(PaymentConceptLookupJpaRepository lookupJpaRepository) {
		this.lookupJpaRepository = lookupJpaRepository;
	}

	@Override
	public List<FichaConcept> findActiveEnrollmentForProgram(UUID programId, LocalDate onDate) {
		return lookupJpaRepository
				.findActiveTuitionForProgram(PaymentConceptStatus.ACTIVE, PaymentConceptType.ENROLLMENT,
						programId, onDate)
				.stream().map(c -> new FichaConcept(c.getName(), c.getCost(), c.getCostExternal(), c.isExternal()))
				.toList();
	}
}