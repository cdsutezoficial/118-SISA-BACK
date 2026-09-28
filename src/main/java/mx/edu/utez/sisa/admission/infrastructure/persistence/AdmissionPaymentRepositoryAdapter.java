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
	 * Both occupancy counts are scoped by "the program has an ADMISSION concept
	 * that can be paid today": the seat is only taken against a concept the
	 * candidate can actually buy, so a program whose admission window has closed
	 * or that has no admission concept yet does not report as full. A concept
	 * created as ENROLLMENT does not count here \u2014 it is a semester quota, not
	 * an admission fee.
	 */
	@Override
	public long countOccupiedByProgramId(UUID programId, LocalDate onDate) {
		return jpaRepository.countOccupiedByProgramId(programId, AdmissionPaymentStatus.PAID,
				AdmissionPaymentStatus.PENDING, PaymentConceptStatus.ACTIVE, PaymentConceptType.ADMISSION, onDate);
	}

	@Override
	public long countOccupiedByProgramIdExcludingCandidate(UUID programId, UUID candidateId, LocalDate onDate) {
		return jpaRepository.countOccupiedByProgramIdExcludingCandidate(programId, candidateId,
				AdmissionPaymentStatus.PAID, AdmissionPaymentStatus.PENDING, PaymentConceptStatus.ACTIVE,
				PaymentConceptType.ADMISSION, onDate);
	}
}
