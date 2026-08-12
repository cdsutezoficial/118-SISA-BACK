package mx.edu.utez.sisa.identity.domain.port.out;

import mx.edu.utez.sisa.shared.model.Person;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Out-port over the shared-kernel {@link Person}. Historically load-only for
 * Identity's own use cases (design.md — Decision: Person creation is out of
 * scope), extended by plan
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} —
 * {@code CreatePersonUseCase} now writes through {@link #save} for the
 * manual internal-staff-registration flow (distinct from the seed-runner
 * usage {@code save} originally existed for), and {@link #findByCurp}/
 * {@link #findByInstitutionalEmail}/{@link #search} back the new uniqueness
 * checks and paginated listing.
 */
public interface PersonRepository {

	Optional<Person> findById(UUID id);

	Person save(Person person);

	/**
	 * Backs {@code CreatePersonUseCase}'s pre-insert uniqueness check
	 * (query-based, not a caught SQL constraint violation — same convention
	 * as every other uniqueness check in this codebase, e.g.
	 * {@code CreateGenerationUseCaseImpl}'s {@code number} check).
	 */
	Optional<Person> findByCurp(String curp);

	/**
	 * Backs {@code CreatePersonUseCase}'s pre-insert uniqueness check, same
	 * convention as {@link #findByCurp}.
	 */
	Optional<Person> findByInstitutionalEmail(String institutionalEmail);

	/**
	 * Filterable, paginated query backing {@code ListPersonsUseCase} (plan:
	 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.2).
	 * Mirrors {@link UserRepository#search}'s
	 * {@code SearchCriteria}/{@code SearchPage} record pattern for
	 * intra-module consistency. {@code hasUser} is NOT resolved here — the
	 * plan places that computation in {@code ListPersonsUseCaseImpl} (one
	 * {@code userRepository.findByPersonId} check per row), so this out-port
	 * only returns the matching {@link Person} rows themselves.
	 */
	PersonSearchPage search(PersonSearchCriteria criteria);

	/**
	 * @param search optional free-text match against curp, full name, or institutionalEmail
	 * @param page   zero-based page index
	 * @param size   page size
	 */
	record PersonSearchCriteria(String search, int page, int size) {
	}

	/**
	 * @param content       the {@link Person} rows for the requested page
	 * @param totalElements total matching rows across all pages
	 * @param totalPages    total page count for {@code totalElements} at the requested page size
	 */
	record PersonSearchPage(List<Person> content, long totalElements, int totalPages) {
	}
}
