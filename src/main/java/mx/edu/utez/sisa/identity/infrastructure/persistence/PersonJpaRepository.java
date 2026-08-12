package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.shared.model.Person;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link PersonRepositoryAdapter}.
 */
public interface PersonJpaRepository extends JpaRepository<Person, UUID> {

	Optional<Person> findByCurp(String curp);

	Optional<Person> findByInstitutionalEmail(String institutionalEmail);

	/**
	 * Backs {@code ListPersonsUseCase} (plan:
	 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.2).
	 * {@code search} is optional and matches curp, either individual name
	 * field, the concatenated full name, or institutionalEmail — same
	 * {@code (:param IS NULL OR ...)} short-circuit pattern as
	 * {@code UserJpaRepository#search}. An explicit {@code countQuery} is
	 * supplied for the same reason as that query: Spring Data's automatic
	 * count-query derivation is not reliable once the content query has this
	 * many OR branches.
	 */
	@Query(value = """
			SELECT p FROM Person p
			WHERE (:search IS NULL
			       OR LOWER(p.curp) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(p.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(p.lastName1) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(p.lastName2) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(CONCAT(p.firstName, ' ', p.lastName1, ' ', COALESCE(p.lastName2, '')))
			          LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(p.institutionalEmail) LIKE LOWER(CONCAT('%', :search, '%')))
			""",
			countQuery = """
			SELECT COUNT(p) FROM Person p
			WHERE (:search IS NULL
			       OR LOWER(p.curp) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(p.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(p.lastName1) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(p.lastName2) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(CONCAT(p.firstName, ' ', p.lastName1, ' ', COALESCE(p.lastName2, '')))
			          LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(p.institutionalEmail) LIKE LOWER(CONCAT('%', :search, '%')))
			""")
	Page<Person> search(@Param("search") String search, Pageable pageable);
}
