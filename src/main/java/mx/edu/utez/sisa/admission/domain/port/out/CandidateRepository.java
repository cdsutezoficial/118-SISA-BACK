package mx.edu.utez.sisa.admission.domain.port.out;

import mx.edu.utez.sisa.admission.domain.model.Candidate;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for the {@link Candidate} aggregate root. The
 * applicant's {@code Person} is persisted through a separate port
 * ({@code CandidatePersonRepository}) since {@code Person} is a shared-kernel
 * aggregate whose lifecycle reaches across bounded contexts.
 */
public interface CandidateRepository {

	Candidate save(Candidate candidate);

	Optional<Candidate> findById(UUID id);

	/**
	 * Backs {@code RegisterCandidateUseCase}'s folio generation
	 * ({@code ADM-{year}-{seq}:06d} — format inherited from the frontend's
	 * mock ficha). {@code prefix} is {@code "ADM-{year}-"}; the sequence is
	 * derived from the current count, so folios are unique per calendar year
	 * (the v1 approximation of the domain's "único por periodo"; the period
	 * of the chosen {@code ProgramAdmissionConfig} is available to a future
	 * exact-per-period implementation).
	 */
	long countByFolioStartingWith(String prefix);
}