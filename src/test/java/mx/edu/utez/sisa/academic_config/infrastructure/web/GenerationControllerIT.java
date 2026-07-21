package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.GenerationStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase.CreateAcademicDivisionCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase.CreatePeriodCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.CreateAcademicPlanCommand;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration coverage for the {@code /generations} security
 * matchers and business rules: real H2, real JWT filter chain, no mocks —
 * mirrors {@code AcademicPeriodControllerIT}'s style. Written from the start,
 * same as every other aggregate this session. A real
 * {@code AcademicDivision} -> {@code AcademicProgram} -> {@code AcademicPlan}
 * chain plus an {@code AcademicPeriod} are created first to obtain valid
 * {@code planId}/{@code startPeriodId} FKs.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GenerationControllerIT {

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
	private CreateAcademicPeriodUseCase createAcademicPeriodUseCase;

	private UUID programId;

	private UUID planId;

	private UUID startPeriodId;

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
		planId = createAcademicPlanUseCase
				.createPlan(new CreateAcademicPlanCommand(programId, "V-" + UUID.randomUUID().toString().substring(0, 8),
						"2022-2028", "CLAVE-01", LocalDate.of(2022, 1, 10), 9, new BigDecimal("7.0"), 2, false, null))
				.id();
		startPeriodId = createAcademicPeriodUseCase
				.createPeriod(new CreatePeriodCommand("Periodo " + UUID.randomUUID(), 2026, uniquePeriodNumber(),
						PeriodType.CUATRIMESTRAL, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 4, 30),
						LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 20)))
				.id();
	}

	private static int periodNumberCounter = 1;

	private static synchronized int uniquePeriodNumber() {
		// AcademicPeriod enforces (year, periodNumber) uniqueness across ALL
		// periods regardless of which test created them — a fixed counter
		// avoids cross-test collisions within this IT class's shared @SpringBootTest
		// context/transaction-per-method H2 instance.
		return periodNumberCounter++;
	}

	// --- Create ---

	@Test
	void adminCanCreate() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/generations").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(planId, startPeriodId, 1))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.code").value("2026-1")).andExpect(jsonPath("$.programId").value(programId.toString()));
	}

	@Test
	void serviciosEscolaresCanCreate() throws Exception {
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(post("/generations").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(planId, startPeriodId, 2))))
				.andExpect(status().isCreated());
	}

	@Test
	void otherRoleIsForbiddenOnCreate() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(post("/generations").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(planId, startPeriodId, 3))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedCreateReturns401() throws Exception {
		mockMvc.perform(post("/generations").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(planId, startPeriodId, 4))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void createWithNonExistentPlanIdReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/generations").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(UUID.randomUUID(), startPeriodId, 5))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createWithNonExistentStartPeriodIdReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/generations").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(planId, UUID.randomUUID(), 6))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void duplicateNumberWithinSameProgramReturns409() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		mockMvc.perform(post("/generations").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(planId, startPeriodId, 100))))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/generations").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(planId, startPeriodId, 100))))
				.andExpect(status().isConflict());
	}

	@Test
	void multipleGenerationsForSameProgramInSameYearAreAllowed() throws Exception {
		// PO-confirmed 2026-07-20 business rule under real end-to-end
		// conditions: a program CAN open more than one generation in the same
		// calendar year (September intake, January follow-up, September
		// again) — only `number` must differ.
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/generations").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(planId, startPeriodId, 200))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("2026-200"));

		mockMvc.perform(post("/generations").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(planId, startPeriodId, 201))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("2026-201"));
	}

	// --- List ---

	@Test
	void adminCanListFilteredByProgramId() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		mockMvc.perform(post("/generations").header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(planId, startPeriodId, 300))))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/generations").header("Authorization", "Bearer " + token).param("programId",
				programId.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.items").isNotEmpty());
	}

	@Test
	void otherRoleIsForbiddenOnList() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(get("/generations").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedListReturns401() throws Exception {
		mockMvc.perform(get("/generations")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	// --- Get by id ---

	@Test
	void adminCanGetById() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID generationId = createGeneration(token, 400);

		mockMvc.perform(get("/generations/{id}", generationId).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(generationId.toString()));
	}

	@Test
	void getByIdWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(get("/generations/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	// --- Update ---

	@Test
	void adminCanUpdate() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID generationId = createGeneration(token, 500);

		mockMvc.perform(put("/generations/{id}", generationId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody(planId, startPeriodId, 501))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.number").value(501));
	}

	@Test
	void otherRoleIsForbiddenOnUpdate() throws Exception {
		String adminToken = tokenFor(RoleType.ADMIN);
		UUID generationId = createGeneration(adminToken, 502);
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(put("/generations/{id}", generationId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody(planId, startPeriodId, 503))))
				.andExpect(status().isForbidden());
	}

	@Test
	void updateWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(put("/generations/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody(planId, startPeriodId, 504))))
				.andExpect(status().isNotFound());
	}

	@Test
	void updateWithNumberCollidingWithAnotherRecordInSameProgramReturns409() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		createGeneration(token, 505);
		UUID target = createGeneration(token, 506);

		mockMvc.perform(put("/generations/{id}", target).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody(planId, startPeriodId, 505))))
				.andExpect(status().isConflict());
	}

	@Test
	void updateWithUnchangedNumberOnOwnRecordSucceeds() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID generationId = createGeneration(token, 507);

		mockMvc.perform(put("/generations/{id}", generationId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody(planId, startPeriodId, 507))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.number").value(507));
	}

	// --- ChangeStatus ---

	@Test
	void adminCanChangeStatusBothDirections() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID generationId = createGeneration(token, 600);

		mockMvc.perform(patch("/generations/{id}/status", generationId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(GenerationStatus.FINISHED))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FINISHED"));

		mockMvc.perform(patch("/generations/{id}/status", generationId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(GenerationStatus.ACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void serviciosEscolaresCanChangeStatus() throws Exception {
		String adminToken = tokenFor(RoleType.ADMIN);
		UUID generationId = createGeneration(adminToken, 601);
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(patch("/generations/{id}/status", generationId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(GenerationStatus.FINISHED))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FINISHED"));
	}

	@Test
	void otherRoleIsForbiddenOnChangeStatus() throws Exception {
		String adminToken = tokenFor(RoleType.ADMIN);
		UUID generationId = createGeneration(adminToken, 602);
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(patch("/generations/{id}/status", generationId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(GenerationStatus.FINISHED))))
				.andExpect(status().isForbidden());
	}

	@Test
	void changeStatusWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(patch("/generations/{id}/status", UUID.randomUUID()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(GenerationStatus.FINISHED))))
				.andExpect(status().isNotFound());
	}

	private UUID createGeneration(String token, int number) throws Exception {
		var createResult = mockMvc
				.perform(post("/generations").header("Authorization", "Bearer " + token).contentType("application/json")
						.content(objectMapper.writeValueAsString(new CreateBody(planId, startPeriodId, number))))
				.andExpect(status().isCreated()).andReturn();
		JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
		return UUID.fromString(created.get("id").asText());
	}

	private String tokenFor(RoleType role) {
		return jwtService.sign(UUID.randomUUID().toString(), Set.of(role.name()));
	}

	private record CreateBody(UUID planId, UUID startPeriodId, int number) {
	}

	private record UpdateBody(UUID planId, UUID startPeriodId, int number) {
	}

	private record ChangeStatusBody(GenerationStatus status) {
	}
}
