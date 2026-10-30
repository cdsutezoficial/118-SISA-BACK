package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.CandidateProgramChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CandidateProgramChangeJpaRepository extends JpaRepository<CandidateProgramChange, UUID> {

	long countByCandidateId(UUID candidateId);
}
