package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link CandidateRepositoryAdapter}. Extends
 * {@link JpaRepository} (giving {@code save}/{@code findById} for free, used
 * directly by integration tests to seed rows). {@code countByFolioStartingWith}
 * backs the use case's calendar-year-scoped folio sequence — see
 * {@code RegisterCandidateUseCaseImpl#generateFolio}.
 */
public interface CandidateJpaRepository extends JpaRepository<Candidate, UUID> {

	/**
	 * Counts existing folios under the given prefix (e.g. {@code "ADM-2026-"})
	 * — the {@code seq} component of folio {@code ADM-{year}-{seq}:06d}.
	 */
	long countByFolioStartingWith(String prefix);

	/**
	 * Exact folio lookup backing {@code AccessFichaPaymentUseCase}: the
	 * "vuelve a pagar mi ficha" entry point identifies the candidate by folio
	 * before checking the CURP suffix. Derived query — {@code folio} is a
	 * unique business key.
	 */
	Optional<Candidate> findByFolio(String folio);
}