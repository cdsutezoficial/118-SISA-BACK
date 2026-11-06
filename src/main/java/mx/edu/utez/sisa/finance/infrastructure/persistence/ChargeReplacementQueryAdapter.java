package mx.edu.utez.sisa.finance.infrastructure.persistence;

import mx.edu.utez.sisa.finance.domain.port.out.ChargeReplacementQueryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link ChargeReplacementQueryPort} over the career-change history that
 * admission keeps in {@code candidate_program_change}.
 */
@Component
public class ChargeReplacementQueryAdapter implements ChargeReplacementQueryPort {

	private final FinanceProgramChangeJpaRepository programChangeJpaRepository;

	public ChargeReplacementQueryAdapter(FinanceProgramChangeJpaRepository programChangeJpaRepository) {
		this.programChangeJpaRepository = programChangeJpaRepository;
	}

	@Override
	public Optional<UUID> findReplacingChargeId(UUID paymentId) {
		if (paymentId == null) {
			return Optional.empty();
		}
		return programChangeJpaRepository.findReplacingChargeIds(paymentId).stream().findFirst();
	}
}
