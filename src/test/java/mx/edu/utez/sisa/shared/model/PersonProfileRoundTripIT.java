package mx.edu.utez.sisa.shared.model;

import mx.edu.utez.sisa.identity.infrastructure.persistence.PersonJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB (H2) coverage for the Fase B shared-primary-key {@code @OneToOne} +
 * {@code @MapsId} mapping (plan: {@code docs/plans/2026-07-29-person-extension.md}
 * — section 7). The core guarantee under test: each of the 5 child profiles
 * ({@link Address}, {@link HealthProfile}, {@link DiversityProfile},
 * {@link EmploymentInfo}, {@link HighSchoolBackground}) is reachable ONLY via
 * {@code Person}, and — when no child row exists — the getter resolves to a
 * true {@code null} (no row found), never an object with all-null fields.
 *
 * <p>Every test forces {@link TestEntityManager#flush()} +
 * {@link TestEntityManager#clear()} before reloading, so assertions run
 * against a fresh persistence context (the entity is genuinely reloaded from
 * H2, not read back from Hibernate's first-level cache) — this is what makes
 * the null-profile guarantee meaningful rather than trivially true.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class PersonProfileRoundTripIT {

	@Autowired
	private PersonJpaRepository personRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void personWithHealthProfileAttached_roundTripsAllFieldsCorrectly() {
		Person person = newPerson();
		HealthProfile healthProfile = new HealthProfile(true, "Asma controlada", true, "Movilidad reducida",
				BloodType.O_POSITIVE);
		person.setHealthProfile(healthProfile);

		Person saved = personRepository.save(person);
		UUID personId = saved.getId();
		entityManager.flush();
		entityManager.clear();

		Optional<Person> reloaded = personRepository.findById(personId);
		assertThat(reloaded).isPresent();

		HealthProfile reloadedProfile = reloaded.get().getHealthProfile();
		assertThat(reloadedProfile).isNotNull();
		assertThat(reloadedProfile.getPersonId()).isEqualTo(personId);
		assertThat(reloadedProfile.isHasPreexistingCondition()).isTrue();
		assertThat(reloadedProfile.getConditionDescription()).isEqualTo("Asma controlada");
		assertThat(reloadedProfile.isHasDisability()).isTrue();
		assertThat(reloadedProfile.getDisabilityDescription()).isEqualTo("Movilidad reducida");
		assertThat(reloadedProfile.getBloodType()).isEqualTo(BloodType.O_POSITIVE);
	}

	@Test
	void personWithNoProfilesAttached_allFiveProfileGettersReturnNull() {
		Person person = newPerson();

		Person saved = personRepository.save(person);
		UUID personId = saved.getId();
		entityManager.flush();
		entityManager.clear();

		Optional<Person> reloaded = personRepository.findById(personId);
		assertThat(reloaded).isPresent();

		Person reloadedPerson = reloaded.get();
		assertThat(reloadedPerson.getAddress()).isNull();
		assertThat(reloadedPerson.getHealthProfile()).isNull();
		assertThat(reloadedPerson.getDiversityProfile()).isNull();
		assertThat(reloadedPerson.getEmploymentInfo()).isNull();
		assertThat(reloadedPerson.getHighSchoolBackground()).isNull();
	}

	@Test
	void personWithAllFiveProfilesAttached_everyProfileRoundTripsIndependently() {
		Person person = newPerson();
		person.setAddress(new Address("Av. Universidad", "1001", "Depto 4", "Centro", "Cuernavaca", "62000", null,
				null, null, null));
		person.setHealthProfile(new HealthProfile(false, null, false, null, null));
		person.setDiversityProfile(
				new DiversityProfile(false, false, true, false, false, false, false, null, null));
		person.setEmploymentInfo(
				new EmploymentInfo(true, EmploymentType.PERMANENT, "UTEZ", "Docente", null, BigDecimal.valueOf(15000),
						null, null));
		person.setHighSchoolBackground(new HighSchoolBackground("Prepa UTEZ", "Emiliano Zapata", null,
				BigDecimal.valueOf(9.2), true, null, null, null, null, null, null, null, null));

		Person saved = personRepository.save(person);
		UUID personId = saved.getId();
		entityManager.flush();
		entityManager.clear();

		Person reloaded = personRepository.findById(personId).orElseThrow();
		assertThat(reloaded.getAddress()).isNotNull();
		assertThat(reloaded.getAddress().getStreet()).isEqualTo("Av. Universidad");
		assertThat(reloaded.getHealthProfile()).isNotNull();
		assertThat(reloaded.getDiversityProfile()).isNotNull();
		assertThat(reloaded.getDiversityProfile().isSelfIdentifiesIndigenous()).isTrue();
		assertThat(reloaded.getEmploymentInfo()).isNotNull();
		assertThat(reloaded.getEmploymentInfo().getCompanyName()).isEqualTo("UTEZ");
		assertThat(reloaded.getHighSchoolBackground()).isNotNull();
		assertThat(reloaded.getHighSchoolBackground().getSchoolCity()).isEqualTo("Emiliano Zapata");
	}

	private static Person newPerson() {
		String curp = "CURP" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
		return new Person(curp, "Ana", "García", "López", "ana." + UUID.randomUUID() + "@utez.edu.mx");
	}
}
