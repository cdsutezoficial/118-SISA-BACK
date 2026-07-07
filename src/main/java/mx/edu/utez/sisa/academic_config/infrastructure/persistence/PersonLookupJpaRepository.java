package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.shared.model.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data interface backing {@link PersonLookupAdapter}, over the shared
 * {@code person} table. Deliberately a separate interface from
 * {@code identity.PersonJpaRepository} — {@code academic_config} defines its
 * own minimal read-only repository instead of importing identity's port
 * (design.md — Decision: Director validation via own out-port). No custom
 * finder beyond the inherited {@code existsById} is needed.
 */
public interface PersonLookupJpaRepository extends JpaRepository<Person, UUID> {
}
