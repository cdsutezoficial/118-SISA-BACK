package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.port.out.AdmissionQuotaPort;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigNotFoundException;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Locks the config row for the cap and counts occupancy over finance's
 * charges and checkout attempts (see {@link FichaOccupancyJpaRepository}).
 */
@Component
public class AdmissionQuotaAdapter implements AdmissionQuotaPort {

	/** No candidate has this id, so "excluding nobody" is a plain comparison. */
	private static final UUID NOBODY = new UUID(0L, 0L);

	private static final List<PaymentStatus> SETTLED = List.of(PaymentStatus.PAID, PaymentStatus.EXEMPT);

	private final ProgramAdmissionConfigLookupJpaRepository configJpaRepository;
	private final FichaOccupancyJpaRepository occupancyJpaRepository;

	public AdmissionQuotaAdapter(ProgramAdmissionConfigLookupJpaRepository configJpaRepository,
			FichaOccupancyJpaRepository occupancyJpaRepository) {
		this.configJpaRepository = configJpaRepository;
		this.occupancyJpaRepository = occupancyJpaRepository;
	}

	@Override
	public QuotaState lockQuota(UUID admissionConfigId) {
		var config = configJpaRepository.findByIdForUpdate(admissionConfigId)
				.orElseThrow(() -> new ProgramAdmissionConfigNotFoundException(
						"Program admission config not found: " + admissionConfigId));
		return new QuotaState(config.getMaxCandidates());
	}

	@Override
	public long countOccupiedExcluding(UUID admissionConfigId, UUID candidateId, Instant attemptsOpenedSince) {
		return occupancyJpaRepository.countOccupied(admissionConfigId, candidateId == null ? NOBODY : candidateId,
				SETTLED, CheckoutAttemptCloseReason.STARTED, attemptsOpenedSince);
	}
}
