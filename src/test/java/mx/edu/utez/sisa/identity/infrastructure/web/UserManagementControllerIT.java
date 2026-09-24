package mx.edu.utez.sisa.identity.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordHasher;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.shared.model.Person;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration coverage for the 3 new {@code UserController}
 * endpoints (plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.3/4.4/4.5):
 * {@code GET /users/{id}}, {@code DELETE /users/{userId}/roles/{userRoleId}},
 * {@code PATCH /users/{id}/unlock}. Real H2, real JWT filter chain, no
 * mocks — mirrors {@code GenerationControllerIT}'s style. Same caveat as
 * {@code PersonControllerIT}: every caller token here must be backed by a
 * real, already-past-first-access {@code User} row.
 */
@SpringBootTest(properties = { "sisa.security.bootstrap.admin.password=", 
		"sisa.security.bootstrap.servicios-escolares.password=",
		"sisa.security.bootstrap.test-accounts.password=" })
@AutoConfigureMockMvc
class UserManagementControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private UserRoleRepository userRoleRepository;

	@Autowired
	private PasswordHasher passwordHasher;

	@Autowired
	private RoleRepository roleRepository;

	// --- GET /users/{id} ---

	@Test
	void adminCanGetUserDetailWithUserRoleIdsExposed() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID targetId = newPlainUser("target1");
		UUID userRoleId = userRoleRepository.save(new UserRole(targetId, resolveRoleId(RoleType.DOCENTE), null)).getId();

		mockMvc.perform(get("/users/{id}", targetId).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(targetId.toString()))
				.andExpect(jsonPath("$.roles[0].userRoleId").value(userRoleId.toString()))
				.andExpect(jsonPath("$.roles[0].roleKey").value("DOCENTE"));
	}

	@Test
	void serviciosEscolaresIsForbiddenOnGetUserDetail() throws Exception {
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);
		UUID targetId = newPlainUser("target2");

		mockMvc.perform(get("/users/{id}", targetId).header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void otherRoleIsForbiddenOnGetUserDetail() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);
		UUID targetId = newPlainUser("target3");

		mockMvc.perform(get("/users/{id}", targetId).header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedGetUserDetailReturns401() throws Exception {
		UUID targetId = newPlainUser("target4");

		mockMvc.perform(get("/users/{id}", targetId)).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void getUserDetailWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(get("/users/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	// --- DELETE /users/{userId}/roles/{userRoleId} ---

	@Test
	void adminCanRevokeRole() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID targetId = newPlainUser("target5");
		UUID userRoleId = userRoleRepository.save(new UserRole(targetId, resolveRoleId(RoleType.DOCENTE), null)).getId();

		mockMvc.perform(delete("/users/{userId}/roles/{userRoleId}", targetId, userRoleId)
				.header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());

		mockMvc.perform(get("/users/{id}", targetId).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.roles").isEmpty());
	}

	@Test
	void otherRoleIsForbiddenOnRevokeRole() throws Exception {
		String adminToken = tokenFor(RoleType.ADMIN);
		String token = tokenFor(RoleType.DOCENTE);
		UUID targetId = newPlainUser("target6");
		UUID userRoleId = userRoleRepository.save(new UserRole(targetId, resolveRoleId(RoleType.DOCENTE), null)).getId();

		mockMvc.perform(delete("/users/{userId}/roles/{userRoleId}", targetId, userRoleId)
				.header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
	}

	@Test
	void serviciosEscolaresIsForbiddenOnRevokeRole() throws Exception {
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);
		UUID targetId = newPlainUser("target7");
		UUID userRoleId = userRoleRepository.save(new UserRole(targetId, resolveRoleId(RoleType.DOCENTE), null)).getId();

		mockMvc.perform(delete("/users/{userId}/roles/{userRoleId}", targetId, userRoleId)
				.header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
	}

	@Test
	void revokeRoleWithUnknownUserRoleIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID targetId = newPlainUser("target8");

		mockMvc.perform(delete("/users/{userId}/roles/{userRoleId}", targetId, UUID.randomUUID())
				.header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
	}

	@Test
	void revokeRoleBelongingToAnotherUserReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID ownerId = newPlainUser("owner1");
		UUID otherUserId = newPlainUser("other1");
		UUID userRoleId = userRoleRepository.save(new UserRole(ownerId, resolveRoleId(RoleType.DOCENTE), null)).getId();

		// attempt to revoke the owner's role grant while impersonating it as
		// belonging to a different user in the URL (plan 4.4's guarded case)
		mockMvc.perform(delete("/users/{userId}/roles/{userRoleId}", otherUserId, userRoleId)
				.header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());

		// the grant must still exist, untouched, under its real owner
		mockMvc.perform(get("/users/{id}", ownerId).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.roles[0].userRoleId").value(userRoleId.toString()));
	}

	@Test
	void unauthenticatedRevokeRoleReturns401() throws Exception {
		UUID targetId = newPlainUser("target9");
		UUID userRoleId = userRoleRepository.save(new UserRole(targetId, resolveRoleId(RoleType.DOCENTE), null)).getId();

		mockMvc.perform(delete("/users/{userId}/roles/{userRoleId}", targetId, userRoleId))
				.andExpect(status().isUnauthorized());
	}

	// --- PATCH /users/{id}/unlock ---

	@Test
	void adminCanUnlockLockedAccount() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID targetId = newPlainUser("target10");
		User target = userRepository.findById(targetId).orElseThrow();
		target.registerFailedLogin();
		target.registerFailedLogin();
		target.registerFailedLogin();
		userRepository.save(target);

		mockMvc.perform(patch("/users/{id}/unlock", targetId).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.failedLoginAttempts").value(0));
	}

	@Test
	void unlockIsIdempotentOnAlreadyActiveAccount() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID targetId = newPlainUser("target11");

		mockMvc.perform(patch("/users/{id}/unlock", targetId).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void otherRoleIsForbiddenOnUnlock() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);
		UUID targetId = newPlainUser("target12");

		mockMvc.perform(patch("/users/{id}/unlock", targetId).header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void serviciosEscolaresIsForbiddenOnUnlock() throws Exception {
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);
		UUID targetId = newPlainUser("target13");

		mockMvc.perform(patch("/users/{id}/unlock", targetId).header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void unlockWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(patch("/users/{id}/unlock", UUID.randomUUID()).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void unauthenticatedUnlockReturns401() throws Exception {
		UUID targetId = newPlainUser("target14");

		mockMvc.perform(patch("/users/{id}/unlock", targetId)).andExpect(status().isUnauthorized());
	}

	private static String uniqueCurp() {
		return "CURP" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
	}

	/** A plain, already-past-first-access target {@code User} with no roles. */
	private UUID newPlainUser(String emailLocalPart) {
		String email = emailLocalPart + "." + UUID.randomUUID() + "@utez.edu.mx";
		Person person = personRepository.save(new Person(uniqueCurp(), "Target", "Fixture", null, email));
		User user = new User(person.getId(), email, passwordHasher.hash("Sup3rSecret!1"));
		user.changePassword(passwordHasher.hash("Sup3rSecret!1"));
		return userRepository.save(user).getId();
	}

	/**
	 * Same rationale as {@code PersonControllerIT#tokenFor}: every use case
	 * behind these endpoints resolves the JWT subject via
	 * {@code UserRepository#findById}, so the token must be backed by a
	 * real, already-past-first-access {@code User}.
	 */
	private String tokenFor(RoleType role) {
		String email = "caller." + UUID.randomUUID() + "@utez.edu.mx";
		Person person = personRepository.save(new Person(uniqueCurp(), "Caller", "Fixture", null, email));
		User user = new User(person.getId(), email, passwordHasher.hash("Sup3rSecret!1"));
		user.changePassword(passwordHasher.hash("Sup3rSecret!1"));
		User saved = userRepository.save(user);
		userRoleRepository.save(new UserRole(saved.getId(), resolveRoleId(role), null));
		return jwtService.sign(saved.getId().toString(), Set.of(role.name()));
	}

	private UUID resolveRoleId(RoleType roleType) {
		return roleRepository.findByKey(roleType.name()).map(mx.edu.utez.sisa.identity.domain.model.Role::getId)
				.orElseThrow();
	}
}
