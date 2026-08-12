package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
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
 * End-to-end integration coverage for the {@code /program-admission-configs}
 * security matchers and business rules: real H2, real JWT filter chain, no
 * mocks — mirrors {@code GroupControllerIT}'s style. A real
 * {@code AcademicDivision} -> {@code AcademicProgram} -> {@code AcademicPlan}
 * chain, plus an {@code AcademicPeriod} and a {@code Generation} opened
 * against that plan, are created first to obtain valid
 * {@code programId}/{@code periodId}/{@code targetGenerationId} FKs. Year
 * 2030 is used for periods — deliberately distinct from the years already
 * claimed by {@code GenerationControllerIT} (2026) and {@code GroupControllerIT}
 * (2027), since {@code AcademicPeriod} enforces {@code (year, periodNumber)}
 * uniqueness across ALL periods regardless of which test class created them,
 * and all IT classes share the same {@code @SpringBootTest} H2 instance.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProgramAdmissionConfigControllerIT {

	private static final Instant OPENS_AT = Instant.parse("2029-06-01T00:00:00Z");

	private static final Instant CLOSES_AT = Instant.parse("2029-08-01T00:00:00Z");

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

	@Autowired
	private CreateGenerationUseCase createGenerationUseCase;

	private UUID programId;

	private UUID planId;

	private UUID periodId;

	private UUID generationId;

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
						"2029-2035", "CLAVE-01", LocalDate.of(2029, 1, 10), 9, new BigDecimal("7.0"), 2, false, null))
				.id();
		periodId = createPeriod();
		generationId = createGenerationUseCase
				.createGeneration(new CreateGenerationCommand(planId, periodId, uniqueGenerationNumber())).id();
	}

	private UUID createPeriod() {
		return createAcademicPeriodUseCase
				.createPeriod(new CreatePeriodCommand("Periodo " + UUID.randomUUID(), 2030, uniquePeriodNumber(),
						PeriodType.CUATRIMESTRAL, LocalDate.of(2030, 1, 5), LocalDate.of(2030, 4, 30),
						LocalDate.of(2029, 12, 1), LocalDate.of(2029, 12, 20)))
				.id();
	}

	private static int periodNumberCounter = 1;

	private static synchronized int uniquePeriodNumber() {
		return periodNumberCounter++;
	}

	private static int generationNumberCounter = 1;

	private static synchronized int uniqueGenerationNumber() {
		return generationNumberCounter++;
	}

	// --- Create ---

	@Test
	void adminCanCreate() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/program-admission-configs").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(programId, periodId, generationId))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.selectionStatus").value("IN_REVIEW"))
				.andExpect(jsonPath("$.programId").value(programId.toString()));
	}

	@Test
	void serviciosEscolaresCanCreate() throws Exception {
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(post("/program-admission-configs").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(programId, periodId, generationId))))
				.andExpect(status().isCreated());
	}

	@Test
	void otherRoleIsForbiddenOnCreate() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(post("/program-admission-configs").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(programId, periodId, generationId))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedCreateReturns401() throws Exception {
		mockMvc.perform(post("/program-admission-configs").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(programId, periodId, generationId))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void createWithNonExistentProgramIdReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/program-admission-configs").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(UUID.randomUUID(), periodId, generationId))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createWithNonExistentPeriodIdReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/program-admission-configs").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(programId, UUID.randomUUID(), generationId))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createWithNonExistentTargetGenerationIdReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/program-admission-configs").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(programId, periodId, UUID.randomUUID()))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createWithMaxCandidatesNotGreaterThanZeroReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/program-admission-configs").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreateBodyFull(programId, periodId, generationId, true, 0, OPENS_AT, CLOSES_AT))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createWithClosesAtBeforeOpensAtReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/program-admission-configs").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreateBodyFull(programId, periodId, generationId, true, 50, CLOSES_AT, OPENS_AT))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void duplicateProgramIdPeriodIdPairReturns409() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		mockMvc.perform(post("/program-admission-configs").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(programId, periodId, generationId))))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/program-admission-configs").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(programId, periodId, generationId))))
				.andExpect(status().isConflict());
	}

	// --- List ---

	@Test
	void adminCanListFilteredByProgramId() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		mockMvc.perform(post("/program-admission-configs").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(programId, periodId, generationId))))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/program-admission-configs").header("Authorization", "Bearer " + token)
				.param("programId", programId.toString())).andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isNotEmpty());
	}

	@Test
	void otherRoleIsForbiddenOnList() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(get("/program-admission-configs").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedListReturns401() throws Exception {
		mockMvc.perform(get("/program-admission-configs")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	// --- Get by id ---

	@Test
	void adminCanGetById() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID configId = createConfig(token, programId, periodId, generationId);

		mockMvc.perform(get("/program-admission-configs/{id}", configId).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(configId.toString()));
	}

	@Test
	void getByIdWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(
				get("/program-admission-configs/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	// --- Update ---

	@Test
	void adminCanUpdate() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID configId = createConfig(token, programId, periodId, generationId);

		mockMvc.perform(put("/program-admission-configs/{id}", configId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreateBodyFull(programId, periodId, generationId, false, 75, OPENS_AT, CLOSES_AT))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.maxCandidates").value(75))
				.andExpect(jsonPath("$.isOffered").value(false));
	}

	@Test
	void otherRoleIsForbiddenOnUpdate() throws Exception {
		String adminToken = tokenFor(RoleType.ADMIN);
		UUID configId = createConfig(adminToken, programId, periodId, generationId);
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(put("/program-admission-configs/{id}", configId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(programId, periodId, generationId))))
				.andExpect(status().isForbidden());
	}

	@Test
	void updateWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(put("/program-admission-configs/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(programId, periodId, generationId))))
				.andExpect(status().isNotFound());
	}

	@Test
	void updateWithPairCollidingWithAnotherRecordReturns409() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID otherPeriodId = createPeriod();
		createConfig(token, programId, periodId, generationId);
		UUID target = createConfig(token, programId, otherPeriodId, generationId);

		mockMvc.perform(put("/program-admission-configs/{id}", target).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(programId, periodId, generationId))))
				.andExpect(status().isConflict());
	}

	@Test
	void updateWithUnchangedPairOnOwnRecordSucceeds() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID configId = createConfig(token, programId, periodId, generationId);

		mockMvc.perform(put("/program-admission-configs/{id}", configId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreateBodyFull(programId, periodId, generationId, true, 60, OPENS_AT, CLOSES_AT))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.maxCandidates").value(60));
	}

	// --- ChangeStatus ---

	@Test
	void adminCanChangeStatusBothDirections() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID configId = createConfig(token, programId, periodId, generationId);

		mockMvc.perform(patch("/program-admission-configs/{id}/status", configId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(ProgramAdmissionConfigStatus.CLOSED))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));

		mockMvc.perform(patch("/program-admission-configs/{id}/status", configId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(ProgramAdmissionConfigStatus.OPEN))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));
	}

	@Test
	void changeStatusNeverTouchesSelectionStatus() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		UUID configId = createConfig(token, programId, periodId, generationId);

		mockMvc.perform(patch("/program-admission-configs/{id}/status", configId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(ProgramAdmissionConfigStatus.CLOSED))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.selectionStatus").value("IN_REVIEW"));
	}

	@Test
	void serviciosEscolaresCanChangeStatus() throws Exception {
		String adminToken = tokenFor(RoleType.ADMIN);
		UUID configId = createConfig(adminToken, programId, periodId, generationId);
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(patch("/program-admission-configs/{id}/status", configId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(ProgramAdmissionConfigStatus.CLOSED))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
	}

	@Test
	void otherRoleIsForbiddenOnChangeStatus() throws Exception {
		String adminToken = tokenFor(RoleType.ADMIN);
		UUID configId = createConfig(adminToken, programId, periodId, generationId);
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(patch("/program-admission-configs/{id}/status", configId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(ProgramAdmissionConfigStatus.CLOSED))))
				.andExpect(status().isForbidden());
	}

	@Test
	void changeStatusWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(patch("/program-admission-configs/{id}/status", UUID.randomUUID())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(ProgramAdmissionConfigStatus.CLOSED))))
				.andExpect(status().isNotFound());
	}

	private UUID createConfig(String token, UUID programId, UUID periodId, UUID generationId) throws Exception {
		var createResult = mockMvc
				.perform(post("/program-admission-configs").header("Authorization", "Bearer " + token)
						.contentType("application/json")
						.content(objectMapper.writeValueAsString(new CreateBody(programId, periodId, generationId))))
				.andExpect(status().isCreated()).andReturn();
		JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
		return UUID.fromString(created.get("id").asText());
	}

	private String tokenFor(RoleType role) {
		return jwtService.sign(UUID.randomUUID().toString(), Set.of(role.name()));
	}

	private record CreateBody(UUID programId, UUID periodId, UUID targetGenerationId, boolean isOffered,
			int maxCandidates, Instant opensAt, Instant closesAt) {
		CreateBody(UUID programId, UUID periodId, UUID targetGenerationId) {
			this(programId, periodId, targetGenerationId, true, 50, OPENS_AT, CLOSES_AT);
		}
	}

	private record CreateBodyFull(UUID programId, UUID periodId, UUID targetGenerationId, boolean isOffered,
			int maxCandidates, Instant opensAt, Instant closesAt) {
	}

	private record ChangeStatusBody(ProgramAdmissionConfigStatus status) {
	}
}
