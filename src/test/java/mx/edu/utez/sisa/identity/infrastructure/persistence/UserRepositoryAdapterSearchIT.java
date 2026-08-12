package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.model.UserStatus;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository.UserSearchCriteria;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository.UserSearchPage;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository.UserWithPerson;
import mx.edu.utez.sisa.shared.model.Person;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB (H2) coverage for {@link UserRepositoryAdapter#search}
 * (01-identidad.md — ListUsersUseCase). Proves the specific bug class this
 * query was designed to avoid: filtering by {@code roleType} through a naive
 * {@code JOIN UserRole} would duplicate a user's row once per matching role
 * and corrupt both the total count and the page contents. The production
 * query instead uses a correlated {@code EXISTS} subquery (see
 * {@link UserJpaRepository#search}), so a user with several roles — even
 * several matching the requested filter twice over via different UserRole
 * rows — is counted and returned exactly once.
 */
@DataJpaTest
@Import(UserRepositoryAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class UserRepositoryAdapterSearchIT {

	@Autowired
	private UserRepositoryAdapter adapter;

	@Autowired
	private UserJpaRepository userJpaRepository;

	@Autowired
	private PersonJpaRepository personJpaRepository;

	@Autowired
	private UserRoleJpaRepository userRoleJpaRepository;

	@Test
	void roleTypeFilterDoesNotDuplicateUsersWithMultipleMatchingRoles() {
		Person person = personJpaRepository.save(newPerson("Ana", "García", "López", "ana.garcia@utez.edu.mx"));
		User user = userJpaRepository.save(new User(person.getId(), "ana.garcia@utez.edu.mx", "hash"));
		UUID divisionA = UUID.randomUUID();
		UUID divisionB = UUID.randomUUID();
		// two UserRole rows of the SAME roleType (division-scoped, different
		// divisions) — the exact shape that would break a naive JOIN
		userRoleJpaRepository.save(new UserRole(user.getId(), RoleType.DIRECTOR_DIVISION, divisionA));
		userRoleJpaRepository.save(new UserRole(user.getId(), RoleType.DIRECTOR_DIVISION, divisionB));

		UserSearchPage page = adapter
				.search(new UserSearchCriteria(RoleType.DIRECTOR_DIVISION, null, null, 0, 20, null));

		assertThat(page.totalElements()).isEqualTo(1L);
		assertThat(page.totalPages()).isEqualTo(1);
		assertThat(page.content()).hasSize(1);
		assertThat(page.content().get(0).user().getId()).isEqualTo(user.getId());
	}

	@Test
	void divisionIdCombinedWithRoleTypeExcludesRightRoleWrongDivision() {
		Person person = personJpaRepository.save(newPerson("Laura", "Ruiz", null, "laura.ruiz@utez.edu.mx"));
		User user = userJpaRepository.save(new User(person.getId(), "laura.ruiz@utez.edu.mx", "hash"));
		UUID targetDivision = UUID.randomUUID();
		UUID otherDivision = UUID.randomUUID();
		// right roleType, but scoped to a DIFFERENT division than requested
		userRoleJpaRepository.save(new UserRole(user.getId(), RoleType.DIRECTOR_DIVISION, otherDivision));

		UserSearchPage page = adapter
				.search(new UserSearchCriteria(RoleType.DIRECTOR_DIVISION, null, null, 0, 20, targetDivision));

		assertThat(page.totalElements()).isZero();
		assertThat(page.content()).isEmpty();
	}

	@Test
	void divisionIdCombinedWithRoleTypeMatchesRightRoleAndRightDivision() {
		Person person = personJpaRepository.save(newPerson("Mario", "Sosa", null, "mario.sosa@utez.edu.mx"));
		User user = userJpaRepository.save(new User(person.getId(), "mario.sosa@utez.edu.mx", "hash"));
		UUID targetDivision = UUID.randomUUID();
		userRoleJpaRepository.save(new UserRole(user.getId(), RoleType.DIRECTOR_DIVISION, targetDivision));

		UserSearchPage page = adapter
				.search(new UserSearchCriteria(RoleType.DIRECTOR_DIVISION, null, null, 0, 20, targetDivision));

		assertThat(page.totalElements()).isEqualTo(1L);
		assertThat(page.content()).hasSize(1);
		assertThat(page.content().get(0).user().getId()).isEqualTo(user.getId());
	}

	@Test
	void divisionIdCombinedWithRoleTypeCountsUserOnceWhenOnlyOneOfSeveralRolesMatchesBoth() {
		Person person = personJpaRepository.save(newPerson("Nora", "Vega", null, "nora.vega@utez.edu.mx"));
		User user = userJpaRepository.save(new User(person.getId(), "nora.vega@utez.edu.mx", "hash"));
		UUID targetDivision = UUID.randomUUID();
		UUID otherDivision = UUID.randomUUID();
		// three UserRole rows: only the second one matches BOTH roleType and
		// divisionId — the naive-JOIN failure mode would still duplicate the
		// user's row per role, even though just one row satisfies the combined filter
		userRoleJpaRepository.save(new UserRole(user.getId(), RoleType.DIRECTOR_DIVISION, otherDivision));
		userRoleJpaRepository.save(new UserRole(user.getId(), RoleType.DIRECTOR_DIVISION, targetDivision));
		userRoleJpaRepository.save(new UserRole(user.getId(), RoleType.DOCENTE, targetDivision));

		UserSearchPage page = adapter
				.search(new UserSearchCriteria(RoleType.DIRECTOR_DIVISION, null, null, 0, 20, targetDivision));

		assertThat(page.totalElements()).isEqualTo(1L);
		assertThat(page.totalPages()).isEqualTo(1);
		assertThat(page.content()).hasSize(1);
		assertThat(page.content().get(0).user().getId()).isEqualTo(user.getId());
	}

	@Test
	void divisionIdAloneWithoutRoleTypeHasNoEffect() {
		Person person = personJpaRepository.save(newPerson("Oscar", "Diaz", null, "oscar.diaz@utez.edu.mx"));
		userJpaRepository.save(new User(person.getId(), "oscar.diaz@utez.edu.mx", "hash"));

		UserSearchPage page = adapter
				.search(new UserSearchCriteria(null, null, null, 0, 20, UUID.randomUUID()));

		// divisionId lives inside the roleType-gated EXISTS, so without roleType
		// it is never evaluated: the user (who has no roles at all) still
		// matches — divisionId alone is a permissive no-op, not validated as
		// "requires roleType to also be set"
		assertThat(page.totalElements()).isEqualTo(1L);
	}

	@Test
	void roleTypeFilterExcludesUsersWithoutAMatchingRole() {
		Person person = personJpaRepository.save(newPerson("Juan", "Pérez", null, "juan.perez@utez.edu.mx"));
		User user = userJpaRepository.save(new User(person.getId(), "juan.perez@utez.edu.mx", "hash"));
		userRoleJpaRepository.save(new UserRole(user.getId(), RoleType.DOCENTE, null));

		UserSearchPage page = adapter.search(new UserSearchCriteria(RoleType.ADMIN, null, null, 0, 20, null));

		assertThat(page.totalElements()).isZero();
		assertThat(page.content()).isEmpty();
	}

	@Test
	void statusFilterMatchesExactStatus() {
		Person activePerson = personJpaRepository.save(newPerson("Active", "User", null, "active@utez.edu.mx"));
		userJpaRepository.save(new User(activePerson.getId(), "active@utez.edu.mx", "hash"));
		Person lockedPerson = personJpaRepository.save(newPerson("Locked", "User", null, "locked@utez.edu.mx"));
		User lockedUser = userJpaRepository.save(new User(lockedPerson.getId(), "locked@utez.edu.mx", "hash"));
		lockedUser.registerFailedLogin();
		lockedUser.registerFailedLogin();
		lockedUser.registerFailedLogin();
		userJpaRepository.save(lockedUser);

		UserSearchPage page = adapter.search(new UserSearchCriteria(null, UserStatus.LOCKED, null, 0, 20, null));

		assertThat(page.totalElements()).isEqualTo(1L);
		assertThat(page.content().get(0).user().getUsername()).isEqualTo("locked@utez.edu.mx");
	}

	@Test
	void searchMatchesUsernameOrPersonFullNameAcrossFields() {
		Person person = personJpaRepository.save(newPerson("Ana", "García", "López", "ana.garcia@utez.edu.mx"));
		userJpaRepository.save(new User(person.getId(), "ana.garcia@utez.edu.mx", "hash"));
		Person other = personJpaRepository.save(newPerson("Carlos", "Mendoza", null, "carlos.mendoza@utez.edu.mx"));
		userJpaRepository.save(new User(other.getId(), "carlos.mendoza@utez.edu.mx", "hash"));

		UserSearchPage byFirstName = adapter.search(new UserSearchCriteria(null, null, "ana", 0, 20, null));
		UserSearchPage byLastName = adapter.search(new UserSearchCriteria(null, null, "garcía", 0, 20, null));
		UserSearchPage byUsername = adapter.search(new UserSearchCriteria(null, null, "carlos.mendoza", 0, 20, null));
		UserSearchPage byFullNameSpanningFields = adapter
				.search(new UserSearchCriteria(null, null, "ana garcía", 0, 20, null));

		assertThat(byFirstName.totalElements()).isEqualTo(1L);
		assertThat(byLastName.totalElements()).isEqualTo(1L);
		assertThat(byUsername.totalElements()).isEqualTo(1L);
		assertThat(byFullNameSpanningFields.totalElements()).isEqualTo(1L);
	}

	@Test
	void paginationMetadataReflectsTotalElementsAcrossMultiplePages() {
		for (int i = 0; i < 5; i++) {
			Person person = personJpaRepository.save(newPerson("First" + i, "Last" + i, null, "user" + i + "@utez.edu.mx"));
			userJpaRepository.save(new User(person.getId(), "user" + i + "@utez.edu.mx", "hash"));
		}

		UserSearchPage firstPage = adapter.search(new UserSearchCriteria(null, null, null, 0, 2, null));
		UserSearchPage secondPage = adapter.search(new UserSearchCriteria(null, null, null, 1, 2, null));

		assertThat(firstPage.totalElements()).isEqualTo(5L);
		assertThat(firstPage.totalPages()).isEqualTo(3);
		assertThat(firstPage.content()).hasSize(2);
		assertThat(secondPage.content()).hasSize(2);
	}

	@Test
	void resultRowsCarryTheLinkedPerson() {
		Person person = personJpaRepository.save(newPerson("Ana", "García", "López", "ana.garcia2@utez.edu.mx"));
		userJpaRepository.save(new User(person.getId(), "ana.garcia2@utez.edu.mx", "hash"));

		UserSearchPage page = adapter.search(new UserSearchCriteria(null, null, "ana.garcia2", 0, 20, null));

		assertThat(page.content()).hasSize(1);
		UserWithPerson row = page.content().get(0);
		assertThat(row.person().getFirstName()).isEqualTo("Ana");
		assertThat(row.person().getLastName1()).isEqualTo("García");
	}

	private static Person newPerson(String firstName, String lastName1, String lastName2, String institutionalEmail) {
		String curp = "CURP" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
		return new Person(curp, firstName, lastName1, lastName2, institutionalEmail);
	}
}
