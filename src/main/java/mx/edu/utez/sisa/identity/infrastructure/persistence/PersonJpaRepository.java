package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.shared.model.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data interface backing {@link PersonRepositoryAdapter}. No custom
 * finder is needed: {@code findById}/{@code save} are inherited.
 */
public interface PersonJpaRepository extends JpaRepository<Person, UUID> {
}
