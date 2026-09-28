package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.port.out.AdmissionQuotaPort;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigNotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * JPA-backed {@link AdmissionQuotaPort}: the lock is
 * {@link ProgramAdmissionConfigLookupJpaRepository#findByIdForUpdate}, and the
 * cap is read from that same locked entity.
 *
 * <p>Reading the cap from the locked row instead of issuing a second query is
 * what makes the comparison trustworthy. If the cap came from anywhere else, a
 * staff member editing {@code maxCandidates} concurrently could widen or narrow
 * the limit between the count and the decision without anyone holding a lock
 * against it.
 */
@Component
public class AdmissionQuotaAdapter implements AdmissionQuotaPort {

	private final ProgramAdmissionConfigLookupJpaRepository configJpaRepository;

	public AdmissionQuotaAdapter(ProgramAdmissionConfigLookupJpaRepository configJpaRepository) {
		this.configJpaRepository = configJpaRepository;
	}

	@Override
	public QuotaState lockQuota(UUID admissionConfigId) {
		var config = configJpaRepository.findByIdForUpdate(admissionConfigId)
				.orElseThrow(() -> new ProgramAdmissionConfigNotFoundException(
						"Program admission config not found: " + admissionConfigId));
		return new QuotaState(config.getMaxCandidates(), config.getProgramId());
	}
}
