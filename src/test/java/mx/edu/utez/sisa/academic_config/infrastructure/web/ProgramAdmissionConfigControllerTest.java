package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.academic_config.domain.model.SelectionStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeProgramAdmissionConfigStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeProgramAdmissionConfigStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetProgramAdmissionConfigUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListProgramAdmissionConfigsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListProgramAdmissionConfigsUseCase.ListProgramAdmissionConfigsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListProgramAdmissionConfigsUseCase.ListProgramAdmissionConfigsResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListProgramAdmissionConfigsUseCase.ProgramAdmissionConfigSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.OpenProgramAdmissionUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.OpenProgramAdmissionUseCase.OpenProgramAdmissionCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.OpenProgramAdmissionUseCase.ProgramAdmissionConfigResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateProgramAdmissionConfigUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateProgramAdmissionConfigUseCase.UpdateProgramAdmissionConfigCommand;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateProgramAdmissionConfigException;
import mx.edu.utez.sisa.academic_config.shared.exception.GenerationReferenceNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidProgramAdmissionConfigDataException;
import mx.edu.utez.sisa.academic_config.shared.exception.PeriodNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.ProgramAdmissionConfigNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.ProgramNotFoundException;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.identity.infrastructure.security.PermissionCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin-controller tests for {@link ProgramAdmissionConfigController},
 * mirroring {@code GenerationControllerTest}'s style.
 */
@WebMvcTest(ProgramAdmissionConfigController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProgramAdmissionConfigControllerTest {

	private static final Instant OPENS_AT = Instant.parse("2026-01-01T00:00:00Z");

	private static final Instant CLOSES_AT = Instant.parse("2026-03-01T00:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ListProgramAdmissionConfigsUseCase listProgramAdmissionConfigsUseCase;

	@MockitoBean
	private OpenProgramAdmissionUseCase openProgramAdmissionUseCase;

	@MockitoBean
	private GetProgramAdmissionConfigUseCase getProgramAdmissionConfigUseCase;

	@MockitoBean
	private UpdateProgramAdmissionConfigUseCase updateProgramAdmissionConfigUseCase;

	@MockitoBean
	private ChangeProgramAdmissionConfigStatusUseCase changeProgramAdmissionConfigStatusUseCase;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private PermissionCache permissionCache;

	private UUID callerId;

	@BeforeEach
	void setUp() {
		callerId = UUID.randomUUID();
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				callerId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void createProgramAdmissionConfigReturns201WithBody() throws Exception {
		UUID configId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		UUID periodId = UUID.randomUUID();
		UUID generationId = UUID.randomUUID();
		when(openProgramAdmissionUseCase.openProgramAdmission(
				new OpenProgramAdmissionCommand(programId, periodId, generationId, true, 50, OPENS_AT, CLOSES_AT)))
				.thenReturn(new ProgramAdmissionConfigResult(configId, programId, periodId, generationId, true, 50,
						OPENS_AT, CLOSES_AT, ProgramAdmissionConfigStatus.OPEN, SelectionStatus.IN_REVIEW));

		mockMvc.perform(post("/program-admission-configs").contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreateBody(programId, periodId, generationId, true, 50, OPENS_AT, CLOSES_AT))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(configId.toString()))
				.andExpect(jsonPath("$.status").value("OPEN")).andExpect(jsonPath("$.selectionStatus").value("IN_REVIEW"))
				.andExpect(jsonPath("$.maxCandidates").value(50));
	}

	@Test
	void createProgramAdmissionConfigIgnoresClientSuppliedSelectionStatusField() throws Exception {
		// CreateProgramAdmissionConfigRequest has no `selectionStatus` property
		// at all — an extra unknown field in the JSON body must simply be
		// ignored, not rejected or echoed back.
		UUID configId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		UUID periodId = UUID.randomUUID();
		UUID generationId = UUID.randomUUID();
		when(openProgramAdmissionUseCase.openProgramAdmission(
				new OpenProgramAdmissionCommand(programId, periodId, generationId, true, 50, OPENS_AT, CLOSES_AT)))
				.thenReturn(new ProgramAdmissionConfigResult(configId, programId, periodId, generationId, true, 50,
						OPENS_AT, CLOSES_AT, ProgramAdmissionConfigStatus.OPEN, SelectionStatus.IN_REVIEW));

		mockMvc.perform(post("/program-admission-configs").contentType("application/json")
				.content("{\"programId\":\"" + programId + "\",\"periodId\":\"" + periodId + "\",\"targetGenerationId\":\""
						+ generationId + "\",\"isOffered\":true,\"maxCandidates\":50,\"opensAt\":\"" + OPENS_AT
						+ "\",\"closesAt\":\"" + CLOSES_AT + "\",\"selectionStatus\":\"PUBLISHED\"}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.selectionStatus").value("IN_REVIEW"));
	}

	@Test
	void createProgramAdmissionConfigWithMissingProgramIdReturns400() throws Exception {
		mockMvc.perform(post("/program-admission-configs").contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreateBody(null, UUID.randomUUID(), UUID.randomUUID(), true, 50, OPENS_AT, CLOSES_AT))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createProgramAdmissionConfigWithNonExistentProgramIdReturns400() throws Exception {
		when(openProgramAdmissionUseCase.openProgramAdmission(any()))
				.thenThrow(new ProgramNotFoundException("Academic program not found: x"));

		mockMvc.perform(post("/program-admission-configs").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(UUID.randomUUID(), UUID.randomUUID(),
						UUID.randomUUID(), true, 50, OPENS_AT, CLOSES_AT))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createProgramAdmissionConfigWithNonExistentPeriodIdReturns400() throws Exception {
		when(openProgramAdmissionUseCase.openProgramAdmission(any()))
				.thenThrow(new PeriodNotFoundException("Academic period not found: x"));

		mockMvc.perform(post("/program-admission-configs").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(UUID.randomUUID(), UUID.randomUUID(),
						UUID.randomUUID(), true, 50, OPENS_AT, CLOSES_AT))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createProgramAdmissionConfigWithNonExistentTargetGenerationIdReturns400() throws Exception {
		when(openProgramAdmissionUseCase.openProgramAdmission(any()))
				.thenThrow(new GenerationReferenceNotFoundException("Generation not found: x"));

		mockMvc.perform(post("/program-admission-configs").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(UUID.randomUUID(), UUID.randomUUID(),
						UUID.randomUUID(), true, 50, OPENS_AT, CLOSES_AT))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createProgramAdmissionConfigWithDuplicatePairReturns409() throws Exception {
		when(openProgramAdmissionUseCase.openProgramAdmission(any()))
				.thenThrow(new DuplicateProgramAdmissionConfigException("Program admission config already exists"));

		mockMvc.perform(post("/program-admission-configs").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(UUID.randomUUID(), UUID.randomUUID(),
						UUID.randomUUID(), true, 50, OPENS_AT, CLOSES_AT))))
				.andExpect(status().isConflict());
	}

	@Test
	void createProgramAdmissionConfigWithInvalidDataReturns400() throws Exception {
		when(openProgramAdmissionUseCase.openProgramAdmission(any()))
				.thenThrow(new InvalidProgramAdmissionConfigDataException("closesAt must be after opensAt"));

		mockMvc.perform(post("/program-admission-configs").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(UUID.randomUUID(), UUID.randomUUID(),
						UUID.randomUUID(), true, 50, CLOSES_AT, OPENS_AT))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void listProgramAdmissionConfigsReturns200WithItemsAndPaginationMetadata() throws Exception {
		UUID configId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		ProgramAdmissionConfigSummary summary = new ProgramAdmissionConfigSummary(configId, programId,
				UUID.randomUUID(), UUID.randomUUID(), true, 50, OPENS_AT, CLOSES_AT, ProgramAdmissionConfigStatus.OPEN,
				SelectionStatus.IN_REVIEW);
		when(listProgramAdmissionConfigsUseCase.listProgramAdmissionConfigs(
				new ListProgramAdmissionConfigsQuery(ProgramAdmissionConfigStatus.OPEN, programId, 0, 20)))
				.thenReturn(new ListProgramAdmissionConfigsResult(List.of(summary), 1L, 1, 0, 20));

		mockMvc.perform(get("/program-admission-configs").param("status", "OPEN").param("programId",
				programId.toString())).andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(configId.toString()))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void listProgramAdmissionConfigsDefaultsPageAndSizeWhenOmitted() throws Exception {
		when(listProgramAdmissionConfigsUseCase.listProgramAdmissionConfigs(
				new ListProgramAdmissionConfigsQuery(null, null, 0, 20)))
				.thenReturn(new ListProgramAdmissionConfigsResult(List.of(), 0L, 0, 0, 20));

		mockMvc.perform(get("/program-admission-configs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty());

		verify(listProgramAdmissionConfigsUseCase)
				.listProgramAdmissionConfigs(new ListProgramAdmissionConfigsQuery(null, null, 0, 20));
	}

	@Test
	void getProgramAdmissionConfigReturns200WithBodyWhenFound() throws Exception {
		UUID configId = UUID.randomUUID();
		when(getProgramAdmissionConfigUseCase.getById(configId))
				.thenReturn(new ProgramAdmissionConfigResult(configId, UUID.randomUUID(), UUID.randomUUID(),
						UUID.randomUUID(), true, 50, OPENS_AT, CLOSES_AT, ProgramAdmissionConfigStatus.OPEN,
						SelectionStatus.IN_REVIEW));

		mockMvc.perform(get("/program-admission-configs/{id}", configId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(configId.toString()));
	}

	@Test
	void getProgramAdmissionConfigReturns404WhenNotFound() throws Exception {
		UUID unknownId = UUID.randomUUID();
		when(getProgramAdmissionConfigUseCase.getById(unknownId))
				.thenThrow(new ProgramAdmissionConfigNotFoundException("Program admission config not found: " + unknownId));

		mockMvc.perform(get("/program-admission-configs/{id}", unknownId)).andExpect(status().isNotFound());
	}

	@Test
	void updateProgramAdmissionConfigReturns200WithBody() throws Exception {
		UUID configId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		UUID periodId = UUID.randomUUID();
		UUID generationId = UUID.randomUUID();
		when(updateProgramAdmissionConfigUseCase.updateProgramAdmissionConfig(new UpdateProgramAdmissionConfigCommand(
				configId, programId, periodId, generationId, false, 80, OPENS_AT, CLOSES_AT)))
				.thenReturn(new ProgramAdmissionConfigResult(configId, programId, periodId, generationId, false, 80,
						OPENS_AT, CLOSES_AT, ProgramAdmissionConfigStatus.OPEN, SelectionStatus.IN_REVIEW));

		mockMvc.perform(put("/program-admission-configs/{id}", configId).contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new UpdateBody(programId, periodId, generationId, false, 80, OPENS_AT, CLOSES_AT))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.maxCandidates").value(80));
	}

	@Test
	void updateProgramAdmissionConfigReturns404WhenNotFound() throws Exception {
		when(updateProgramAdmissionConfigUseCase.updateProgramAdmissionConfig(any()))
				.thenThrow(new ProgramAdmissionConfigNotFoundException("Program admission config not found: x"));

		mockMvc.perform(put("/program-admission-configs/{id}", UUID.randomUUID()).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody(UUID.randomUUID(), UUID.randomUUID(),
						UUID.randomUUID(), true, 50, OPENS_AT, CLOSES_AT))))
				.andExpect(status().isNotFound());
	}

	@Test
	void updateProgramAdmissionConfigWithDuplicatePairReturns409() throws Exception {
		when(updateProgramAdmissionConfigUseCase.updateProgramAdmissionConfig(any()))
				.thenThrow(new DuplicateProgramAdmissionConfigException("Program admission config already exists"));

		mockMvc.perform(put("/program-admission-configs/{id}", UUID.randomUUID()).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody(UUID.randomUUID(), UUID.randomUUID(),
						UUID.randomUUID(), true, 50, OPENS_AT, CLOSES_AT))))
				.andExpect(status().isConflict());
	}

	@Test
	void changeStatusReturns200WithUpdatedStatus() throws Exception {
		UUID configId = UUID.randomUUID();
		when(changeProgramAdmissionConfigStatusUseCase
				.changeStatus(new ChangeStatusCommand(callerId, configId, ProgramAdmissionConfigStatus.CLOSED)))
				.thenReturn(new ProgramAdmissionConfigResult(configId, UUID.randomUUID(), UUID.randomUUID(),
						UUID.randomUUID(), true, 50, OPENS_AT, CLOSES_AT, ProgramAdmissionConfigStatus.CLOSED,
						SelectionStatus.IN_REVIEW));

		mockMvc.perform(patch("/program-admission-configs/" + configId + "/status").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(ProgramAdmissionConfigStatus.CLOSED))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));

		verify(changeProgramAdmissionConfigStatusUseCase)
				.changeStatus(new ChangeStatusCommand(callerId, configId, ProgramAdmissionConfigStatus.CLOSED));
	}

	@Test
	void changeStatusOfUnknownConfigReturns404() throws Exception {
		UUID configId = UUID.randomUUID();
		when(changeProgramAdmissionConfigStatusUseCase.changeStatus(any()))
				.thenThrow(new ProgramAdmissionConfigNotFoundException("Program admission config not found: " + configId));

		mockMvc.perform(patch("/program-admission-configs/" + configId + "/status").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(ProgramAdmissionConfigStatus.CLOSED))))
				.andExpect(status().isNotFound());
	}

	private record CreateBody(UUID programId, UUID periodId, UUID targetGenerationId, boolean isOffered,
			int maxCandidates, Instant opensAt, Instant closesAt) {
	}

	private record UpdateBody(UUID programId, UUID periodId, UUID targetGenerationId, boolean isOffered,
			int maxCandidates, Instant opensAt, Instant closesAt) {
	}

	private record ChangeStatusBody(ProgramAdmissionConfigStatus status) {
	}
}
