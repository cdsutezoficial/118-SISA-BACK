package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase.CreateAcademicDivisionCommand;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration coverage for the {@code /programs} security
 * matchers (Phase 7, design.md's "Security matcher (order matters)"): real
 * H2, real JWT filter chain, no mocks — mirrors
 * {@code AcademicDivisionControllerIT}'s style. A real {@code AcademicDivision}
 * is created first via {@link CreateAcademicDivisionUseCase} to obtain a
 * valid {@code divisionId} FK, since {@code divisionId} is required for
 * every program creation (spec: "divisionId MUST be required").
 */
@SpringBootTest
@AutoConfigureMockMvc
class AcademicProgramControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private CreateAcademicDivisionUseCase createAcademicDivisionUseCase;

	private UUID divisionId;

	@BeforeEach
	void setUp() {
		divisionId = createAcademicDivisionUseCase
				.createDivision(new CreateAcademicDivisionCommand("Division " + UUID.randomUUID(),
						"DIV-" + UUID.randomUUID().toString().substring(0, 8), "desc", null))
				.id();
	}

	@Test
	void adminHasFullCrudAccess() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		exerciseFullCrud(token, "ISW-ADMIN-" + UUID.randomUUID().toString().substring(0, 6),
				"Ingeniería en Software (Admin)");
	}

	@Test
	void serviciosEscolaresHasFullCrudAccess() throws Exception {
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);
		exerciseFullCrud(token, "ISW-SE-" + UUID.randomUUID().toString().substring(0, 6),
				"Ingeniería en Software (SE)");
	}

	@Test
	void otherRoleIsForbiddenOnCreate() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(post("/programs").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreateBody(divisionId, "Forbidden Program", "Forbidden Program", "FRB-01",
								AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, null))))
				.andExpect(status().isForbidden());
	}

	@Test
	void otherRoleIsForbiddenOnList() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(get("/programs").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void getUnknownProgramReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(get("/programs/" + UUID.randomUUID()).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void otherRoleIsForbiddenOnGetById() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(get("/programs/" + UUID.randomUUID()).header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedCreateReturns401() throws Exception {
		mockMvc.perform(post("/programs").contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreateBody(divisionId, "Unauth Program", "Unauth Program", "UNA-01",
								AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, null))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void unauthenticatedListReturns401() throws Exception {
		mockMvc.perform(get("/programs")).andExpect(status().isUnauthorized());
	}

	private void exerciseFullCrud(String token, String code, String name) throws Exception {
		var createResult = mockMvc
				.perform(post("/programs").header("Authorization", "Bearer " + token).contentType("application/json")
						.content(objectMapper.writeValueAsString(new CreateBody(divisionId, name, name, code,
								AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, "desc"))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE")).andReturn();
		JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
		UUID programId = UUID.fromString(created.get("id").asText());

		mockMvc.perform(put("/programs/" + programId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody(divisionId, name + " Updated", name, code,
						AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, "desc updated"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value(name + " Updated"));

		mockMvc.perform(get("/programs/" + programId).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(programId.toString()))
				.andExpect(jsonPath("$.name").value(name + " Updated"));

		mockMvc.perform(get("/programs").header("Authorization", "Bearer " + token).param("search", code))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items").isNotEmpty());

		mockMvc.perform(get("/programs").header("Authorization", "Bearer " + token).param("divisionId",
				divisionId.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.items").isNotEmpty());

		mockMvc.perform(patch("/programs/" + programId + "/status").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new StatusBody(ProgramStatus.INACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));

		mockMvc.perform(patch("/programs/" + programId + "/status").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new StatusBody(ProgramStatus.ACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	private String tokenFor(RoleType role) {
		return jwtService.sign(UUID.randomUUID().toString(), Set.of(role.name()));
	}

	private record CreateBody(UUID divisionId, String name, String offerName, String code, AcademicLevel level,
			ProgramModality modality, UUID continuityProgramId, String description) {
	}

	private record UpdateBody(UUID divisionId, String name, String offerName, String code, AcademicLevel level,
			ProgramModality modality, UUID continuityProgramId, String description) {
	}

	private record StatusBody(ProgramStatus status) {
	}
}
