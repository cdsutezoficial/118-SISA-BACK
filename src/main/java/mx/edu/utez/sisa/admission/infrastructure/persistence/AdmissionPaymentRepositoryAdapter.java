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

	@Override
	public long countOccupiedByProgramId(UUID programId, LocalDate onDate) {
		return jpaRepository.countOccupiedByProgramId(programId, AdmissionPaymentStatus.PAID,
				AdmissionPaymentStatus.PENDING, PaymentConceptStatus.ACTIVE, PaymentConceptType.ENROLLMENT, onDate);
	}

	@Override
	public long countOccupiedByProgramIdExcludingCandidate(UUID programId, UUID candidateId, LocalDate onDate) {
		return jpaRepository.countOccupiedByProgramIdExcludingCandidate(programId, candidateId,
				AdmissionPaymentStatus.PAID, AdmissionPaymentStatus.PENDING, PaymentConceptStatus.ACTIVE,
				PaymentConceptType.ENROLLMENT, onDate);
	}
}
