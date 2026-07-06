package mx.edu.utez.sisa.identity.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordHasher;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.shared.model.Person;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration coverage for tasks 6.1-6.5 (design.md's Testing
 * Strategy "Integration" row): real H2, real BCrypt/JWT adapters, and the
 * full Spring Security filter chain — no mocks. Fixtures are seeded directly
 * via the out-ports (Person creation is out of scope for Identity per
 * design.md, and pre-existing ADMIN state must not depend on
 * {@code AdminSeedRunner}, which is covered separately by {@link
 * mx.edu.utez.sisa.identity.infrastructure.bootstrap.AdminSeedIT}).
 *
 * <p>Test methods are intentionally ordered and share one {@code @SpringBootTest}
 * context ({@code @TestInstance(PER_CLASS)} so instance fields carry state
 * between steps): each scenario group builds on state left behind by the
 * previous one, mirroring the single growing flow described by tasks 6.1-6.5.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowIT {

	private static final String FLOW_ADMIN_USERNAME = "flow.admin@utez.edu.mx";
	private static final String FLOW_ADMIN_PASSWORD = "Sup3rSecret!1";
	private static final String NEW_USER_TEMP_PASSWORD = "TempPass!1";
	private static final String NEW_USER_CHANGED_PASSWORD = "ChangedPass!1";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private UserRoleRepository userRoleRepository;

	@Autowired
	private PasswordHasher passwordHasher;

	private UUID newUserId;
	private String newUserAccessToken;

	@BeforeAll
	void seedFlowAdmin() {
		Person person = personRepository.save(newPerson(FLOW_ADMIN_USERNAME));
		User user = new User(person.getId(), FLOW_ADMIN_USERNAME, passwordHasher.hash(FLOW_ADMIN_PASSWORD));
		// Bypass the API: the seeded ADMIN fixture must already be past
		// first-access (mustChangePassword = false) so step 6.1 can exercise
		// protected ADMIN endpoints immediately.
		user.changePassword(passwordHasher.hash(FLOW_ADMIN_PASSWORD));
		User savedAdmin = userRepository.save(user);
		userRoleRepository.save(new UserRole(savedAdmin.getId(), RoleType.ADMIN, null));
	}

	private static Person newPerson(String institutionalEmail) {
		String curp = "CURP" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
		return new Person(curp, "Test", "Fixture", null, institutionalEmail);
	}

	@Test
	@Order(1)
	void loginThenCreateUserThenRefreshThenReuseAccessToken() throws Exception {
		// login as the ACTIVE, already-past-first-access flow admin
		var loginResult = mockMvc
				.perform(post("/auth/login").contentType("application/json")
						.content(objectMapper.writeValueAsString(new LoginBody(FLOW_ADMIN_USERNAME, FLOW_ADMIN_PASSWORD))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(false)).andReturn();
		JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
		String adminAccessToken = loginBody.get("accessToken").asText();
		String adminRefreshToken = loginBody.get("refreshToken").asText();

		// protected POST /users as ADMIN
		Person newUserPerson = personRepository.save(newPerson("pending.user@utez.edu.mx"));
		var createUserResult = mockMvc
				.perform(post("/users").header("Authorization", "Bearer " + adminAccessToken)
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(new CreateUserBody(newUserPerson.getId(), NEW_USER_TEMP_PASSWORD))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.mustChangePassword").value(true)).andReturn();
		JsonNode createUserBody = objectMapper.readTree(createUserResult.getResponse().getContentAsString());
		newUserId = UUID.fromString(createUserBody.get("userId").asText());

		// POST /auth/refresh
		var refreshResult = mockMvc
				.perform(post("/auth/refresh").contentType("application/json")
						.content(objectMapper.writeValueAsString(new RefreshBody(adminRefreshToken))))
				.andExpect(status().isOk()).andReturn();
		JsonNode refreshBody = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
		String refreshedAdminAccessToken = refreshBody.get("accessToken").asText();
		assertThat(refreshedAdminAccessToken).isNotBlank();

		// reuse the refreshed access token against a protected endpoint
		mockMvc.perform(post("/users/" + newUserId + "/roles")
				.header("Authorization", "Bearer " + refreshedAdminAccessToken).contentType("application/json")
				.content(objectMapper.writeValueAsString(new AssignRoleBody(RoleType.ADMIN, null))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.roleType").value("ADMIN"));
	}

	@Test
	@Order(2)
	void mustChangePasswordBlocksThenChangePasswordUnblocks() throws Exception {
		// first-access login still issues tokens
		var loginResult = mockMvc
				.perform(post("/auth/login").contentType("application/json")
						.content(objectMapper.writeValueAsString(new LoginBody("pending.user@utez.edu.mx", NEW_USER_TEMP_PASSWORD))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true)).andReturn();
		JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
		newUserAccessToken = loginBody.get("accessToken").asText();

		// blocked: newUser already has ROLE_ADMIN (assigned in step 1) but
		// still owes a password change, so the domain guard rejects with 403
		// before any real work happens
		mockMvc.perform(post("/users/" + newUserId + "/roles").header("Authorization", "Bearer " + newUserAccessToken)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new AssignRoleBody(RoleType.DOCENTE, null))))
				.andExpect(status().isForbidden());

		// unblock
		mockMvc.perform(post("/auth/change-password").header("Authorization", "Bearer " + newUserAccessToken)
				.contentType("application/json")
				.content(objectMapper
						.writeValueAsString(new ChangePasswordBody(NEW_USER_TEMP_PASSWORD, NEW_USER_CHANGED_PASSWORD))))
				.andExpect(status().isNoContent());

		// same still-valid access token now succeeds — the mustChangePassword
		// check is against current DB state, not a token claim
		mockMvc.perform(post("/users/" + newUserId + "/roles").header("Authorization", "Bearer " + newUserAccessToken)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new AssignRoleBody(RoleType.DOCENTE, null))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.roleType").value("DOCENTE"));
	}

	@Test
	@Order(3)
	void threeFailedLoginsLockTheAccount() throws Exception {
		String username = "lockout.user@utez.edu.mx";
		String correctPassword = "CorrectPass!1";
		Person person = personRepository.save(newPerson(username));
		userRepository.save(new User(person.getId(), username, passwordHasher.hash(correctPassword)));

		for (int attempt = 1; attempt <= 3; attempt++) {
			mockMvc.perform(post("/auth/login").contentType("application/json")
					.content(objectMapper.writeValueAsString(new LoginBody(username, "wrong-password"))))
					.andExpect(status().isUnauthorized());
		}

		// account is now LOCKED — even the correct password is rejected
		mockMvc.perform(post("/auth/login").contentType("application/json")
				.content(objectMapper.writeValueAsString(new LoginBody(username, correctPassword))))
				.andExpect(status().isLocked());
	}

	@Test
	@Order(4)
	void refreshTokenOfSinceLockedUserIsRejected() throws Exception {
		String username = "since-locked.user@utez.edu.mx";
		String correctPassword = "CorrectPass!1";
		Person person = personRepository.save(newPerson(username));
		userRepository.save(new User(person.getId(), username, passwordHasher.hash(correctPassword)));

		var loginResult = mockMvc
				.perform(post("/auth/login").contentType("application/json")
						.content(objectMapper.writeValueAsString(new LoginBody(username, correctPassword))))
				.andExpect(status().isOk()).andReturn();
		String refreshTokenIssuedWhileActive = objectMapper.readTree(loginResult.getResponse().getContentAsString())
				.get("refreshToken").asText();

		// lock the account via 3 failed logins on "another device"
		for (int attempt = 1; attempt <= 3; attempt++) {
			mockMvc.perform(post("/auth/login").contentType("application/json")
					.content(objectMapper.writeValueAsString(new LoginBody(username, "wrong-password"))))
					.andExpect(status().isUnauthorized());
		}

		mockMvc.perform(post("/auth/refresh").contentType("application/json")
				.content(objectMapper.writeValueAsString(new RefreshBody(refreshTokenIssuedWhileActive))))
				.andExpect(status().isLocked());
	}

	@Test
	@Order(5)
	void refreshOfMustChangePasswordUserStillSucceeds() throws Exception {
		String username = "must-change.user@utez.edu.mx";
		String password = "CorrectPass!1";
		Person person = personRepository.save(newPerson(username));
		// User constructor always starts mustChangePassword = true
		userRepository.save(new User(person.getId(), username, passwordHasher.hash(password)));

		var loginResult = mockMvc
				.perform(post("/auth/login").contentType("application/json")
						.content(objectMapper.writeValueAsString(new LoginBody(username, password))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true)).andReturn();
		String refreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("refreshToken")
				.asText();

		mockMvc.perform(post("/auth/refresh").contentType("application/json")
				.content(objectMapper.writeValueAsString(new RefreshBody(refreshToken))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
	}

	private record LoginBody(String username, String password) {
	}

	private record RefreshBody(String refreshToken) {
	}

	private record ChangePasswordBody(String currentPassword, String newPassword) {
	}

	private record CreateUserBody(UUID personId, String temporaryPassword) {
	}

	private record AssignRoleBody(RoleType roleType, UUID divisionId) {
	}
}
