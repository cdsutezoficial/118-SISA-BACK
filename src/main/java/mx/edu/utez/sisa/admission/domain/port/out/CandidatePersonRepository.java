package mx.edu.utez.sisa.admission.domain.port.out;

import mx.edu.utez.sisa.shared.model.Person;

import java.util.Optional;
import java.util.UUID;

/**
 * Shared-kernel {@code Person} persistence out-port for the admission
 * bounded context. {@code admission} deliberately defines its OWN minimal
 * repository over the shared {@code person} table instead of importing
 * {@code identity}'s {@code PersonJpaRepository} port — identical decision
 * to {@code PersonLookupJpaRepository}'s rationale (design.md — cross-module
 * ports stay out; each module owns its read/write access to the shared
 * kernel). Saving a {@code Person} cascades its 5 child profiles
 * ({@code Address}, {@code HealthProfile}, {@code DiversityProfile},
 * {@code EmploymentInfo}, {@code HighSchoolBackground}) via
 * {@code CascadeType.ALL} on {@code Person}'s {@code @OneToOne} relations.
 */
public interface CandidatePersonRepository {

	Person save(Person person);

	Optional<Person> findByCurp(String curp);

	Optional<Person> findById(UUID id);
}