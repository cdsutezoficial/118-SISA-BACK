package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import org.springframework.stereotype.Component;

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
}