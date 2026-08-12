package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.GroupStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevelType;
import mx.edu.utez.sisa.academic_config.domain.port.in.AddPlanLevelUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.AddPlanLevelUseCase.AddPlanLevelCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase.CreateAcademicDivisionCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase.CreatePeriodCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.CreateAcademicPlanCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase.CreateAcademicProgramCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGenerationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGenerationUseCase.CreateGenerationCommand;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import mx.edu.utez.sisa.shared.model.RoleType;
import mx.edu.utez.sisa.shared.model.Shift;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration coverage for the {@code /groups} security matchers
 * and business rules: real H2, real JWT filter chain, no mocks — mirrors
 * {@code GenerationControllerIT}'s style. A real {@code AcademicDivision} ->
 * {@code AcademicProgram} -> {@code AcademicPlan} (with one
 * {@code PlanLevel}) chain, plus an {@code AcademicPeriod} and a
 * {@code Generation} opened against that plan, are created first to obtain
 * valid {@code generationId}/{@code periodId}/{@code planLevelId} FKs.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GroupControllerIT {

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

	@Autowired
	private CreateAcademicPlanUseCase createAcademicPlanUseCase;

	@Autowired
	private AddPlanLevelUseCase addPlanLevelUseCase;

	@Autowired
	private CreateAcademicPeriodUseCase createAcademicPeriodUseCase;

	@Autowired
	private CreateGenerationUseCase createGenerationUseCase;

	private UUID programId;

	private UUID generationId;

	private UUID periodId;

	private UUID planLevelId;

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
		UUID planId = createAcademicPlanUseCase
				.createPlan(new CreateAcademicPlanCommand(programId, "V-" + UUID.randomUUID().toString().substring(0, 8),
						"2022-2028", "CLAVE-01", LocalDate.of(2022, 1, 10), 9, new BigDecimal("7.0"), 2, false, null))
				.id();
		planLevelId = addPlanLevelUseCase
				.addLevel(new AddPlanLevelCommand(planId, 3, PlanLevelType.REGULAR, "Tercer cuatrimestre")).id();
		periodId = createAcademicPeriodUseCase
				// Year 2027 (not 2026) deliberately — GenerationControllerIT's own
				// uniquePeriodNumber() counter also starts at 1 for year 2026, and
				// both IT classes share the same @SpringBootTest H2 instance, so
				// reusing 2026 here collided with periods it already inserted.
				.createPeriod(new CreatePeriodCommand("Periodo " + UUID.randomUUID(), 2027, uniquePeriodNumber(),
						PeriodType.CUATRIMESTRAL, LocalDate.of(2027, 1, 5), LocalDate.of(2027, 4, 30),
						LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 20)))
				.id();
		generationId = createGenerationUseCase
				.createGeneration(new CreateGenerationCommand(planId, periodId, uniqueGenerationNumber())).id();
	}

	private static int periodNumberCounter = 1;

	private static synchronized int uniquePeriodNumber() {
		// AcademicPeriod enforces (year, periodNumber) uniqueness across ALL
		// periods regardless of which test created them — a fixed counter
		// avoids cross-test collisions within this IT class's shared
		// @SpringBootTest context/transaction-per-method H2 instance.
		return periodNumberCounter++;
	}

	private static int generationNumberCounter = 1;

	private static synchronized int uniqueGenerationNumber() {
		// Same rationale as uniquePeriodNumber — Generation.number is unique
		// per programId, and each test in this class uses a fresh programId,
		// but a fixed counter keeps the setUp deterministic regardless.
		return generationNumberCounter++;
	}

	// --- Create ---

	@Test
	void adminCanCreate() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/groups").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(generationId, periodId, planLevelId, "3A", 35,
						Shift.MORNING))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.code").value("3A")).andExpect(jsonPath("$.programId").value(programId.toString()));
	}

	@Test
	void serviciosEscolaresCanCreate() throws Exception {
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(post("/groups").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(generationId, periodId, planLevelId, "3B", 35,
						Shift.MORNING))))
				.andExpect(status().isCreated());
	}

	@Test
	void otherRoleIsForbiddenOnCreate() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(post("/groups").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(generationId, periodId, planLevelId, "3C", 35,
						Shift.MORNING))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedCreateReturns401() throws Exception {
		mockMvc.perform(post("/groups").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(generationId, periodId, planLevelId, "3D", 35,
						Shift.MORNING))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void createWithNonExistentGenerationIdReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/groups").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(UUID.randomUUID(), periodId, planLevelId, "3E",
						35, Shift.MORNING))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createWithPlanLevelIdFromDifferentPlanReturns404() throws Exception {
		// Regression guard: a planLevelId that belongs to a DIFFERENT plan than
		// the one the generation was opened against must be rejected —
		// reuses PlanLevelNotFoundException (404), no new exception type.
		String token = tokenFor(RoleType.ADMIN);
		UUID otherDivisionId = createAcademicDivisionUseCase
				.createDivision(new CreateAcademicDivisionCommand("Division " + UUID.randomUUID(),
						"DIV-" + UUID.randomUUID().toString().substring(0, 8), "desc", null))
				.id();
		UUID otherProgramId = createAcademicProgramUseCase
				.createProgram(new CreateAcademicProgramCommand(otherDivisionId, "Programa " + UUID.randomUUID(),
						"Oferta " + UUID.randomUUID(), "COD-" + UUID.randomUUID().toString().substring(0, 8),
						AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, "desc", null))
				.id();
		UUID otherPlanId = createAcademicPlanUseCase
				.createPlan(new CreateAcademicPlanCommand(otherProgramId,
						"V-" + UUID.randomUUID().toString().substring(0, 8), "2022-2028", "CLAVE-02",
						LocalDate.of(2022, 1, 10), 9, new BigDecimal("7.0"), 2, false, null))
				.id();
		UUID levelOfOtherPlanId = addPlanLevelUseCase
				.addLevel(new AddPlanLevelCommand(otherPlanId, 1, PlanLevelType.REGULAR, "Primer cuatrimestre")).id();

		mockMvc.perform(post("/groups").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(generationId, periodId, levelOfOtherPlanId, "3F",
						35, Shift.MORNING))))
				.andExpect(status().isNotFound());
	}

	@Test
	void createWithNonExistentPeriodIdReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/groups").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(generationId, UUID.randomUUID(), planLevelId,
						"3G", 35, Shift.MORNING))))
				.andExpect(status().isBadRequest());
	}

	// --- List ---

	@Test
	void adminCanListFilteredByGenerationId() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		mockMvc.perform(post("/groups").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(generationId, periodId, planLevelId, "3H", 35,
						Shift.MORNING))))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/groups").header("Authorization", "Bearer " + token).param("generationId",
				generationId.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.items").isNotEmpty());
	}

	@Test
	void otherRoleIsForbiddenOnList() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(get("/groups").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedListReturns401() throws Exception {
		mockMvc.perform(get("/groups")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	// --- Get by id ---

	@Test
	void adminCanGetById() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID groupId = createGroup(token, "3I");

		mockMvc.perform(get("/groups/{id}", groupId).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(groupId.toString()));
	}

	@Test
	void getByIdWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(get("/groups/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	// --- Update ---

	@Test
	void adminCanUpdate() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID groupId = createGroup(token, "3J");

		mockMvc.perform(put("/groups/{id}", groupId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper
						.writeValueAsString(new UpdateBody(generationId, periodId, planLevelId, "3JJ", 40, Shift.AFTERNOON))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("3JJ"))
				.andExpect(jsonPath("$.maxCapacity").value(40)).andExpect(jsonPath("$.shift").value("AFTERNOON"));
	}

	@Test
	void otherRoleIsForbiddenOnUpdate() throws Exception {
		String adminToken = tokenFor(RoleType.ADMIN);
		UUID groupId = createGroup(adminToken, "3K");
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(put("/groups/{id}", groupId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper
						.writeValueAsString(new UpdateBody(generationId, periodId, planLevelId, "3KK", 40, Shift.AFTERNOON))))
				.andExpect(status().isForbidden());
	}

	@Test
	void updateWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(put("/groups/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper
						.writeValueAsString(new UpdateBody(generationId, periodId, planLevelId, "3L", 40, Shift.AFTERNOON))))
				.andExpect(status().isNotFound());
	}

	// --- ChangeStatus ---

	@Test
	void adminCanChangeStatusBothDirections() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID groupId = createGroup(token, "3M");

		mockMvc.perform(patch("/groups/{id}/status", groupId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(GroupStatus.CLOSED))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));

		mockMvc.perform(patch("/groups/{id}/status", groupId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(GroupStatus.OPEN))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));
	}

	@Test
	void closingAnAlreadyClosedGroupIsIdempotent() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID groupId = createGroup(token, "3N");

		mockMvc.perform(patch("/groups/{id}/status", groupId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(GroupStatus.CLOSED))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));

		mockMvc.perform(patch("/groups/{id}/status", groupId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(GroupStatus.CLOSED))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
	}

	@Test
	void serviciosEscolaresCanChangeStatus() throws Exception {
		String adminToken = tokenFor(RoleType.ADMIN);
		UUID groupId = createGroup(adminToken, "3O");
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(patch("/groups/{id}/status", groupId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(GroupStatus.CLOSED))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
	}

	@Test
	void otherRoleIsForbiddenOnChangeStatus() throws Exception {
		String adminToken = tokenFor(RoleType.ADMIN);
		UUID groupId = createGroup(adminToken, "3P");
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(patch("/groups/{id}/status", groupId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(GroupStatus.CLOSED))))
				.andExpect(status().isForbidden());
	}

	@Test
	void changeStatusWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(patch("/groups/{id}/status", UUID.randomUUID()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(GroupStatus.CLOSED))))
				.andExpect(status().isNotFound());
	}

	private UUID createGroup(String token, String code) throws Exception {
		var createResult = mockMvc
				.perform(post("/groups").header("Authorization", "Bearer " + token).contentType("application/json")
						.content(objectMapper
								.writeValueAsString(new CreateBody(generationId, periodId, planLevelId, code, 35, Shift.MORNING))))
				.andExpect(status().isCreated()).andReturn();
		JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
		return UUID.fromString(created.get("id").asText());
	}

	private String tokenFor(RoleType role) {
		return jwtService.sign(UUID.randomUUID().toString(), Set.of(role.name()));
	}

	private record CreateBody(UUID generationId, UUID periodId, UUID planLevelId, String code, int maxCapacity,
			Shift shift) {
	}

	private record UpdateBody(UUID generationId, UUID periodId, UUID planLevelId, String code, int maxCapacity,
			Shift shift) {
	}

	private record ChangeStatusBody(GroupStatus status) {
	}
}
