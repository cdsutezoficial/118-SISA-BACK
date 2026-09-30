package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link AdmissionPaymentRepository} adapter delegating to
 * {@link AdmissionPaymentJpaRepository}.
 */
@Component
public class AdmissionPaymentRepositoryAdapter implements AdmissionPaymentRepository {

	private final AdmissionPaymentJpaRepository jpaRepository;

	public AdmissionPaymentRepositoryAdapter(AdmissionPaymentJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public AdmissionPayment save(AdmissionPayment payment) {
		return jpaRepository.save(payment);
	}

	@Override
	public Optional<AdmissionPayment> findByCandidateId(UUID candidateId) {
		return jpaRepository.findByCandidateId(candidateId);
	}

	@Override
	public long countPaidByAdmissionConfigId(UUID admissionConfigId) {
		return jpaRepository.countPaidByAdmissionConfigId(admissionConfigId, AdmissionPaymentStatus.PAID);
	}

	/**
	 * Both occupancy counts are scoped by "this config's program has an ADMISSION
	 * concept that can be paid today": the seat is only taken against a concept the
	 * candidate can actually buy, so a program whose admission window has closed or
	 * that has no admission concept yet does not report as full. A concept created
	 * as ENROLLMENT does not count here — it is a semester quota, not an admission
	 * fee.
	 */
	@Override
	public long countOccupiedByConfigId(UUID admissionConfigId, LocalDate onDate) {
		return jpaRepository.countOccupiedByConfigId(admissionConfigId, AdmissionPaymentStatus.PAID,
				AdmissionPaymentStatus.PENDING, PaymentConceptStatus.ACTIVE, PaymentConceptType.ADMISSION, onDate);
	}

	@Override
	public long countOccupiedByConfigIdExcludingCandidate(UUID admissionConfigId, UUID candidateId,
			LocalDate onDate) {
		return jpaRepository.countOccupiedByConfigIdExcludingCandidate(admissionConfigId, candidateId,
				AdmissionPaymentStatus.PAID, AdmissionPaymentStatus.PENDING, PaymentConceptStatus.ACTIVE,
				PaymentConceptType.ADMISSION, onDate);
	}
}
