package mx.edu.utez.sisa.finance.infrastructure.persistence;

import mx.edu.utez.sisa.shared.model.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Read-only-by-usage Spring Data lookup over shared-kernel {@code Person},
 * backing {@link PersonQueryAdapter}. {@code finance} validates the subject of
 * a charge; it never creates or edits a person.
 */
public interface FinancePersonJpaRepository extends JpaRepository<Person, UUID> {
}
