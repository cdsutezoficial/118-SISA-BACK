package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link AdmissionPaymentRepositoryAdapter}.
 * {@code findByCandidateId} backs the payment-confirmation flow (one
 * {@code AdmissionPayment} per {@code Candidate} for the ADMISSION_FICHA
 * concept).
 */
public interface AdmissionPaymentJpaRepository extends JpaRepository<AdmissionPayment, UUID> {

	Optional<AdmissionPayment> findByCandidateId(UUID candidateId);
}