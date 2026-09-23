package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.shared.model.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link CandidatePersonRepositoryAdapter},
 * over the shared {@code person} table. Deliberately a separate interface
 * from {@code identity.PersonJpaRepository} — {@code admission} defines its
 * own minimal repository over the shared-kernel {@code Person} aggregate
 * instead of importing identity's port (same rationale as
 * {@code PersonLookupJpaRepository}'s: design.md — each module owns its
 * access to the shared kernel). {@code save} cascades the 5 child profiles
 * via {@code Person}'s {@code CascadeType.ALL} {@code @OneToOne} relations.
 * {@code findByCurp} backs the duplicate-CURP validation (409) in
 * {@code RegisterCandidateUseCaseImpl}.
 */
public interface CandidatePersonJpaRepository extends JpaRepository<Person, UUID> {

	Optional<Person> findByCurp(String curp);
}