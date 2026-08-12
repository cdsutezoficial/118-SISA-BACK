package mx.edu.utez.sisa.identity.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordHasher;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.shared.model.Person;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration coverage for {@code /persons} (plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.1/4.2):
 * real H2, real JWT filter chain, no mocks — mirrors
 * {@code GenerationControllerIT}'s style. Unlike {@code academic_config}'s
 * controllers, every Identity use case here loads the caller {@code User}
 * via {@code UserRepository#findById} and asserts
 * {@code assertCanOperate()} — so, unlike {@code GenerationControllerIT}'s
 * {@code tokenFor()} (which signs an arbitrary random subject with no
 * backing row), the JWT subject here MUST be a real, already-past-first-access
 * {@code User} id or every call fails with 404/{@code UserNotFoundException}
 * before authorization is even relevant.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PersonControllerIT {

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

	@Test
	void adminCanCreatePerson() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/persons").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(newCreateBody("jane.doe1"))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.firstName").value("Jane"))
				.andExpect(jsonPath("$.institutionalEmail").value("jane.doe1@utez.edu.mx"));
	}

	@Test
	void serviciosEscolaresIsForbiddenOnCreate() throws Exception {
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(post("/persons").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(newCreateBody("jane.doe2"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void otherRoleIsForbiddenOnCreate() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(post("/persons").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(newCreateBody("jane.doe3"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedCreateReturns401() throws Exception {
		mockMvc.perform(post("/persons").contentType("application/json")
				.content(objectMapper.writeValueAsString(newCreateBody("jane.doe4"))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void duplicateCurpReturns409() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		CreatePersonBody body = newCreateBody("jane.doe5");
		mockMvc.perform(post("/persons").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(body))).andExpect(status().isCreated());

		CreatePersonBody duplicateCurp = new CreatePersonBody(body.curp(), "Other", "Name", null,
				"different.email@utez.edu.mx");
		mockMvc.perform(post("/persons").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(duplicateCurp))).andExpect(status().isConflict());
	}

	@Test
	void duplicateInstitutionalEmailReturns409() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		CreatePersonBody body = newCreateBody("jane.doe6");
		mockMvc.perform(post("/persons").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(body))).andExpect(status().isCreated());

		CreatePersonBody duplicateEmail = new CreatePersonBody(uniqueCurp(), "Other", "Name", null, body.institutionalEmail());
		mockMvc.perform(post("/persons").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(duplicateEmail))).andExpect(status().isConflict());
	}

	@Test
	void createWithoutInstitutionalEmailReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/persons").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper
						.writeValueAsString(new CreatePersonBody(uniqueCurp(), "Jane", "Doe", null, ""))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void adminCanListPersons() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		mockMvc.perform(post("/persons").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(newCreateBody("jane.doe7")))).andExpect(status().isCreated());

		mockMvc.perform(get("/persons").header("Authorization", "Bearer " + token).param("search", "jane.doe7"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items").isNotEmpty())
				.andExpect(jsonPath("$.items[0].hasUser").value(false));
	}

	@Test
	void serviciosEscolaresCanListPersons() throws Exception {
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(get("/persons").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
	}

	@Test
	void otherRoleIsForbiddenOnList() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(get("/persons").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedListReturns401() throws Exception {
		mockMvc.perform(get("/persons")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void listPersonsMarksHasUserTrueForPersonWithAnAccount() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		var createResult = mockMvc
				.perform(post("/persons").header("Authorization", "Bearer " + token).contentType("application/json")
						.content(objectMapper.writeValueAsString(newCreateBody("jane.doe8"))))
				.andExpect(status().isCreated()).andReturn();
		JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
		UUID personId = UUID.fromString(created.get("id").asText());
		userRepository.save(new User(personId, "jane.doe8@utez.edu.mx", passwordHasher.hash("temp")));

		mockMvc.perform(get("/persons").header("Authorization", "Bearer " + token).param("search", "jane.doe8"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].hasUser").value(true));
	}

	private CreatePersonBody newCreateBody(String emailLocalPart) {
		return new CreatePersonBody(uniqueCurp(), "Jane", "Doe", null, emailLocalPart + "@utez.edu.mx");
	}

	private static String uniqueCurp() {
		return "CURP" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
	}

	/**
	 * Creates a real, already-past-first-access {@code User} carrying
	 * {@code role} and signs a JWT with ITS real id as subject — unlike
	 * {@code GenerationControllerIT#tokenFor}, a random unbacked subject
	 * would fail {@code UserRepository#findById} inside every one of these
	 * use cases before authorization is even reached.
	 */
	private String tokenFor(RoleType role) {
		String email = "caller." + UUID.randomUUID() + "@utez.edu.mx";
		Person person = personRepository.save(new Person(uniqueCurp(), "Caller", "Fixture", null, email));
		User user = new User(person.getId(), email, passwordHasher.hash("Sup3rSecret!1"));
		user.changePassword(passwordHasher.hash("Sup3rSecret!1"));
		User saved = userRepository.save(user);
		userRoleRepository.save(new UserRole(saved.getId(), role, null));
		return jwtService.sign(saved.getId().toString(), Set.of(role.name()));
	}

	private record CreatePersonBody(String curp, String firstName, String lastName1, String lastName2,
			String institutionalEmail) {
	}
}
