package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.PlanStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase.CreateAcademicDivisionCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase.CreateAcademicProgramCommand;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration coverage for the {@code /plans} security matchers
 * and the full CRUD + nested level/subject flows (Phase 7, design.md's
 * "Security matcher (order matters)"): real H2, real JWT filter chain, no
 * mocks — mirrors {@code AcademicProgramControllerIT}'s style. A real
 * {@code AcademicDivision} and {@code AcademicProgram} are created first to
 * obtain a valid {@code programId} FK (spec: "programId MUST be required").
 */
@SpringBootTest
@AutoConfigureMockMvc
class AcademicPlanControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private CreateAcademicDivisionUseCase createAcademicDivisionUseCase;

	@Autowired
	private CreateAcademicProgramUseCase createAcademicProgramUseCase;

	private UUID programId;

	@BeforeEach
	void setUp() {
		UUID divisionId = createAcademicDivisionUseCase
				.createDivision(new CreateAcademicDivisionCommand("Division " + UUID.randomUUID(),
						"DIV-" + UUID.randomUUID().toString().substring(0, 8), "desc", null))
				.id();
		programId = createAcademicProgramUseCase
				.createProgram(new CreateAcademicProgramCommand(divisionId, "Programa " + UUID.randomUUID(),
						"Oferta " + UUID.randomUUID(), "COD-" + UUID.randomUUID().toString().substring(0, 8),
						AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, "desc", null))
				.id();
	}

	@Test
	void adminHasFullCrudAccessIncludingNestedLevelsAndSubjects() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		exerciseFullCrud(token, "2022-A-" + UUID.randomUUID().toString().substring(0, 6));
	}

	@Test
	void serviciosEscolaresHasFullCrudAccessIncludingNestedLevelsAndSubjects() throws Exception {
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);
		exerciseFullCrud(token, "2022-B-" + UUID.randomUUID().toString().substring(0, 6));
	}

	@Test
	void otherRoleIsForbiddenOnCreate() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(post("/plans").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreatePlanBody(programId, "FORBIDDEN-01", "2022-2028",
						"CLAVE-01", LocalDate.of(2022, 1, 10), 9, new BigDecimal("7.0"), 2, false, null))))
				.andExpect(status().isForbidden());
	}

	@Test
	void otherRoleIsForbiddenOnList() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(get("/plans").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
	}

	@Test
	void getUnknownPlanReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(get("/plans/" + UUID.randomUUID()).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void otherRoleIsForbiddenOnGetById() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(get("/plans/" + UUID.randomUUID()).header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedCreateReturns401() throws Exception {
		mockMvc.perform(post("/plans").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreatePlanBody(programId, "UNAUTH-01", "2022-2028",
						"CLAVE-01", LocalDate.of(2022, 1, 10), 9, new BigDecimal("7.0"), 2, false, null))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void unauthenticatedListReturns401() throws Exception {
		mockMvc.perform(get("/plans")).andExpect(status().isUnauthorized());
	}

	@Test
	void createPlanWithNegativeMaxExtraordinaryExamsPerPeriodReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/plans").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreatePlanBody(programId,
						"NEG-" + UUID.randomUUID().toString().substring(0, 6), "2022-2028", "CLAVE-NEG",
						LocalDate.of(2022, 1, 10), 9, new BigDecimal("7.0"), -1, false, null))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void updatePlanWithNegativeMaxExtraordinaryExamsPerPeriodReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		String version = "UPD-NEG-" + UUID.randomUUID().toString().substring(0, 6);
		var createResult = mockMvc
				.perform(post("/plans").header("Authorization", "Bearer " + token).contentType("application/json")
						.content(objectMapper.writeValueAsString(new CreatePlanBody(programId, version, "2022-2028",
								"CLAVE-" + version, LocalDate.of(2022, 1, 10), 9, new BigDecimal("7.0"), 2, false,
								null))))
				.andExpect(status().isCreated()).andReturn();
		JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
		UUID planId = UUID.fromString(created.get("id").asText());

		mockMvc.perform(put("/plans/" + planId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdatePlanBody(version, "2022-2029", "CLAVE-" + version,
						LocalDate.of(2022, 1, 10), 9, new BigDecimal("7.0"), -1, false, null))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createPlanWithOutOfRangeMinPassingGradeReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/plans").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreatePlanBody(programId,
						"GRADE-" + UUID.randomUUID().toString().substring(0, 6), "2022-2028", "CLAVE-GRADE",
						LocalDate.of(2022, 1, 10), 9, new BigDecimal("70"), 2, false, null))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void addLevelWithLevelNumberOutsideTotalLevelsRangeReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		String version = "LVL-" + UUID.randomUUID().toString().substring(0, 6);
		var createResult = mockMvc
				.perform(post("/plans").header("Authorization", "Bearer " + token).contentType("application/json")
						.content(objectMapper.writeValueAsString(new CreatePlanBody(programId, version, "2022-2028",
								"CLAVE-" + version, LocalDate.of(2022, 1, 10), 9, new BigDecimal("7.0"), 2, false,
								null))))
				.andExpect(status().isCreated()).andReturn();
		JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
		UUID planId = UUID.fromString(created.get("id").asText());

		mockMvc.perform(post("/plans/" + planId + "/levels").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new AddLevelBody(99, "REGULAR", "Fuera de rango"))))
				.andExpect(status().isBadRequest());
	}

	private void exerciseFullCrud(String token, String version) throws Exception {
		var createResult = mockMvc
				.perform(post("/plans").header("Authorization", "Bearer " + token).contentType("application/json")
						.content(objectMapper.writeValueAsString(new CreatePlanBody(programId, version, "2022-2028",
								"CLAVE-" + version, LocalDate.of(2022, 1, 10), 9, new BigDecimal("7.0"), 2, false, null))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE")).andReturn();
		JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
		UUID planId = UUID.fromString(created.get("id").asText());

		mockMvc.perform(put("/plans/" + planId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdatePlanBody(version, "2022-2029", "CLAVE-" + version,
						LocalDate.of(2022, 1, 10), 9, new BigDecimal("8.0"), 3, false, null))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.minPassingGrade").value(8.0));

		mockMvc.perform(get("/plans/" + planId).header("Authorization", "Bearer " + token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(planId.toString()));

		mockMvc.perform(get("/plans").header("Authorization", "Bearer " + token).param("programId",
				programId.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.items").isNotEmpty());

		var levelResult = mockMvc
				.perform(post("/plans/" + planId + "/levels").header("Authorization", "Bearer " + token)
						.contentType("application/json")
						.content(objectMapper
								.writeValueAsString(new AddLevelBody(1, "REGULAR", "Primer cuatrimestre"))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.levelNumber").value(1)).andReturn();
		JsonNode level = objectMapper.readTree(levelResult.getResponse().getContentAsString());
		UUID levelId = UUID.fromString(level.get("id").asText());

		mockMvc.perform(put("/plans/" + planId + "/levels/" + levelId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new AddLevelBody(1, "REGULAR", "Primer cuatri actualizado"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Primer cuatri actualizado"));

		var subjectResult = mockMvc
				.perform(post("/plans/" + planId + "/levels/" + levelId + "/subjects")
						.header("Authorization", "Bearer " + token).contentType("application/json")
						.content(objectMapper.writeValueAsString(
								new AddSubjectBody("MAT101", "Matemáticas I", 8, 5, 2, 1, "CORE", true,
										UUID.randomUUID()))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("MAT101")).andReturn();
		JsonNode subject = objectMapper.readTree(subjectResult.getResponse().getContentAsString());
		UUID subjectId = UUID.fromString(subject.get("id").asText());

		mockMvc.perform(
				put("/plans/" + planId + "/levels/" + levelId + "/subjects/" + subjectId)
						.header("Authorization", "Bearer " + token).contentType("application/json")
						.content(objectMapper.writeValueAsString(
								new AddSubjectBody("MAT101", "Matemáticas I Avanzada", 8, 5, 2, 1, "CORE", true,
										UUID.randomUUID()))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Matemáticas I Avanzada"));

		mockMvc.perform(delete("/plans/" + planId + "/levels/" + levelId + "/subjects/" + subjectId)
				.header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());

		mockMvc.perform(delete("/plans/" + planId + "/levels/" + levelId).header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());

		mockMvc.perform(patch("/plans/" + planId + "/status").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new StatusBody(PlanStatus.INACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));

		mockMvc.perform(patch("/plans/" + planId + "/status").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new StatusBody(PlanStatus.ACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	private String tokenFor(RoleType role) {
		return jwtService.sign(UUID.randomUUID().toString(), Set.of(role.name()));
	}

	private record CreatePlanBody(UUID programId, String version, String validityPeriod, String titulationKey,
			LocalDate effectiveFrom, int totalLevels, BigDecimal minPassingGrade, int maxExtraordinaryExamsPerPeriod,
			boolean requiresSocialService, UUID socialServiceMinLevelId) {
	}

	private record UpdatePlanBody(String version, String validityPeriod, String titulationKey,
			LocalDate effectiveFrom, int totalLevels, BigDecimal minPassingGrade, int maxExtraordinaryExamsPerPeriod,
			boolean requiresSocialService, UUID socialServiceMinLevelId) {
	}

	private record AddLevelBody(int levelNumber, String type, String description) {
	}

	private record AddSubjectBody(String code, String name, int credits, int weeklyHours, int evaluationUnits,
			int displayOrder, String type, boolean isRetakeable, UUID classificationId) {
	}

	private record StatusBody(PlanStatus status) {
	}
}
