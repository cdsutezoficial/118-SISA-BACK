package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository.PersonSearchCriteria;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository.PersonSearchPage;
import mx.edu.utez.sisa.shared.model.Person;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB (H2) coverage for {@link PersonRepositoryAdapter#search} and the
 * new {@code findByCurp}/{@code findByInstitutionalEmail} uniqueness-check
 * queries (plan: {@code docs/plans/2026-07-28-persons-and-user-management.md}
 * — 4.1/4.2). Mirrors {@code UserRepositoryAdapterSearchIT}'s style.
 */
@DataJpaTest
@Import(PersonRepositoryAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class PersonRepositoryAdapterSearchIT {

	@Autowired
	private PersonRepositoryAdapter adapter;

	@Autowired
	private PersonJpaRepository personJpaRepository;

	@Test
	void findByCurp_findsExistingPerson() {
		Person person = personJpaRepository.save(newPerson("Ana", "García", "López", "ana.garcia@utez.edu.mx"));

		Optional<Person> found = adapter.findByCurp(person.getCurp());

		assertThat(found).isPresent();
		assertThat(found.get().getId()).isEqualTo(person.getId());
	}

	@Test
	void findByCurp_emptyWhenNoMatch() {
		Optional<Person> found = adapter.findByCurp("CURPDOESNOTEXIST01");

		assertThat(found).isEmpty();
	}

	@Test
	void findByInstitutionalEmail_findsExistingPerson() {
		Person person = personJpaRepository.save(newPerson("Juan", "Pérez", null, "juan.perez@utez.edu.mx"));

		Optional<Person> found = adapter.findByInstitutionalEmail("juan.perez@utez.edu.mx");

		assertThat(found).isPresent();
		assertThat(found.get().getId()).isEqualTo(person.getId());
	}

	@Test
	void findByInstitutionalEmail_emptyWhenNoMatch() {
		Optional<Person> found = adapter.findByInstitutionalEmail("no-such-email@utez.edu.mx");

		assertThat(found).isEmpty();
	}

	@Test
	void search_matchesCurpFirstNameLastNameOrEmail() {
		Person person = personJpaRepository.save(newPerson("Ana", "García", "López", "ana.garcia2@utez.edu.mx"));
		personJpaRepository.save(newPerson("Carlos", "Mendoza", null, "carlos.mendoza@utez.edu.mx"));

		PersonSearchPage byFirstName = adapter.search(new PersonSearchCriteria("ana", 0, 20));
		PersonSearchPage byLastName = adapter.search(new PersonSearchCriteria("garcía", 0, 20));
		PersonSearchPage byEmail = adapter.search(new PersonSearchCriteria("carlos.mendoza", 0, 20));
		PersonSearchPage byCurp = adapter.search(new PersonSearchCriteria(person.getCurp(), 0, 20));

		assertThat(byFirstName.totalElements()).isEqualTo(1L);
		assertThat(byLastName.totalElements()).isEqualTo(1L);
		assertThat(byEmail.totalElements()).isEqualTo(1L);
		assertThat(byCurp.totalElements()).isEqualTo(1L);
	}

	@Test
	void search_withoutFilterReturnsAllPersonsPaginated() {
		for (int i = 0; i < 5; i++) {
			personJpaRepository.save(newPerson("First" + i, "Last" + i, null, "person" + i + "@utez.edu.mx"));
		}

		PersonSearchPage firstPage = adapter.search(new PersonSearchCriteria(null, 0, 2));
		PersonSearchPage secondPage = adapter.search(new PersonSearchCriteria(null, 1, 2));

		assertThat(firstPage.totalElements()).isEqualTo(5L);
		assertThat(firstPage.totalPages()).isEqualTo(3);
		assertThat(firstPage.content()).hasSize(2);
		assertThat(secondPage.content()).hasSize(2);
	}

	@Test
	void search_excludesNonMatchingPersons() {
		personJpaRepository.save(newPerson("Ana", "García", "López", "ana.garcia3@utez.edu.mx"));

		PersonSearchPage page = adapter.search(new PersonSearchCriteria("no-such-term-xyz", 0, 20));

		assertThat(page.totalElements()).isZero();
		assertThat(page.content()).isEmpty();
	}

	private static Person newPerson(String firstName, String lastName1, String lastName2, String institutionalEmail) {
		String curp = "CURP" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
		return new Person(curp, firstName, lastName1, lastName2, institutionalEmail);
	}
}
