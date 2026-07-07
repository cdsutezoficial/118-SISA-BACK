package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.DivisionStatus;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration coverage for the {@code /divisions} security
 * matchers (Phase 6, design.md's "Security matcher (order matters)"): real
 * H2, real JWT filter chain, no mocks — mirroring {@code AuthFlowIT}'s and
 * {@code AuthenticationEntryPointTest}'s style. Tokens are signed directly
 * via {@link JwtService} (the {@link mx.edu.utez.sisa.identity.infrastructure.security.JwtAuthenticationFilter}
 * only reads the token's {@code roles} claim, no DB-backed {@code User}
 * lookup — so no fixture users are needed to exercise authorization).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AcademicDivisionControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtService jwtService;

	@Test
	void adminHasFullCrudAccess() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		exerciseFullCrud(token, "ISW-ADMIN", "Ingeniería en Software (Admin)");
	}

	@Test
	void serviciosEscolaresHasFullCrudAccess() throws Exception {
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);
		exerciseFullCrud(token, "ISW-SE", "Ingeniería en Software (SE)");
	}

	@Test
	void otherRoleIsForbiddenOnCreate() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(post("/divisions").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody("Forbidden Division", "FRB", null, null))))
				.andExpect(status().isForbidden());
	}

	@Test
	void otherRoleIsForbiddenOnList() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(get("/divisions").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedCreateReturns401() throws Exception {
		mockMvc.perform(post("/divisions").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody("Unauth Division", "UNA", null, null))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void unauthenticatedListReturns401() throws Exception {
		mockMvc.perform(get("/divisions")).andExpect(status().isUnauthorized());
	}

	private void exerciseFullCrud(String token, String code, String name) throws Exception {
		var createResult = mockMvc
				.perform(post("/divisions").header("Authorization", "Bearer " + token).contentType("application/json")
						.content(objectMapper.writeValueAsString(new CreateBody(name, code, "desc", null))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE")).andReturn();
		JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
		UUID divisionId = UUID.fromString(created.get("id").asText());

		mockMvc.perform(put("/divisions/" + divisionId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody(name + " Updated", code, "desc updated", null))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value(name + " Updated"));

		mockMvc.perform(get("/divisions").header("Authorization", "Bearer " + token).param("search", code))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items").isNotEmpty());

		mockMvc.perform(patch("/divisions/" + divisionId + "/status").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new StatusBody(DivisionStatus.INACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));

		mockMvc.perform(patch("/divisions/" + divisionId + "/status").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new StatusBody(DivisionStatus.ACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	private String tokenFor(RoleType role) {
		return jwtService.sign(UUID.randomUUID().toString(), Set.of(role.name()));
	}

	private record CreateBody(String name, String code, String description, UUID directorPersonId) {
	}

	private record UpdateBody(String name, String code, String description, UUID directorPersonId) {
	}

	private record StatusBody(DivisionStatus status) {
	}
}
