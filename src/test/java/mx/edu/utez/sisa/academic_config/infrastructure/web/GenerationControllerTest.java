package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.GenerationStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeGenerationStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeGenerationStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGenerationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGenerationUseCase.CreateGenerationCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGenerationUseCase.GenerationResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetGenerationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGenerationsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGenerationsUseCase.GenerationSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGenerationsUseCase.ListGenerationsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGenerationsUseCase.ListGenerationsResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateGenerationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateGenerationUseCase.UpdateGenerationCommand;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateGenerationNumberException;
import mx.edu.utez.sisa.academic_config.shared.exception.GenerationNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PeriodNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanNotFoundException;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
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
 * Thin-controller tests for {@link GenerationController}, mirroring
 * {@code AcademicPeriodControllerTest}'s style.
 */
@WebMvcTest(GenerationController.class)
@AutoConfigureMockMvc(addFilters = false)
class GenerationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ListGenerationsUseCase listGenerationsUseCase;

	@MockitoBean
	private CreateGenerationUseCase createGenerationUseCase;

	@MockitoBean
	private GetGenerationUseCase getGenerationUseCase;

	@MockitoBean
	private UpdateGenerationUseCase updateGenerationUseCase;

	@MockitoBean
	private ChangeGenerationStatusUseCase changeGenerationStatusUseCase;

	@MockitoBean
	private JwtService jwtService;

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
	void createGenerationReturns201WithBody() throws Exception {
		UUID generationId = UUID.randomUUID();
		UUID planId = UUID.randomUUID();
		UUID startPeriodId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		when(createGenerationUseCase.createGeneration(new CreateGenerationCommand(planId, startPeriodId, 7)))
				.thenReturn(new GenerationResult(generationId, planId, startPeriodId, programId, 7, "2026-7",
						GenerationStatus.ACTIVE));

		mockMvc.perform(post("/generations").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(planId, startPeriodId, 7))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(generationId.toString()))
				.andExpect(jsonPath("$.code").value("2026-7")).andExpect(jsonPath("$.number").value(7))
				.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void createGenerationIgnoresClientSuppliedCodeField() throws Exception {
		// CreateGenerationRequest has no `code` property at all — an extra
		// unknown field in the JSON body must simply be ignored, not rejected
		// or echoed back.
		UUID generationId = UUID.randomUUID();
		UUID planId = UUID.randomUUID();
		UUID startPeriodId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		when(createGenerationUseCase.createGeneration(new CreateGenerationCommand(planId, startPeriodId, 7)))
				.thenReturn(new GenerationResult(generationId, planId, startPeriodId, programId, 7, "2026-7",
						GenerationStatus.ACTIVE));

		mockMvc.perform(post("/generations").contentType("application/json")
				.content("{\"planId\":\"" + planId + "\",\"startPeriodId\":\"" + startPeriodId
						+ "\",\"number\":7,\"code\":\"HACKED-999\"}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("2026-7"));
	}

	@Test
	void createGenerationWithMissingPlanIdReturns400() throws Exception {
		mockMvc.perform(post("/generations").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(null, UUID.randomUUID(), 7))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createGenerationWithNonExistentPlanIdReturns400() throws Exception {
		when(createGenerationUseCase.createGeneration(any()))
				.thenThrow(new PlanNotFoundException("Academic plan not found: x"));

		mockMvc.perform(post("/generations").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(UUID.randomUUID(), UUID.randomUUID(), 7))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createGenerationWithNonExistentStartPeriodIdReturns400() throws Exception {
		when(createGenerationUseCase.createGeneration(any()))
				.thenThrow(new PeriodNotFoundException("Academic period not found: x"));

		mockMvc.perform(post("/generations").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(UUID.randomUUID(), UUID.randomUUID(), 7))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createGenerationWithDuplicateNumberReturns409() throws Exception {
		when(createGenerationUseCase.createGeneration(any()))
				.thenThrow(new DuplicateGenerationNumberException("Generation number already in use: 7"));

		mockMvc.perform(post("/generations").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody(UUID.randomUUID(), UUID.randomUUID(), 7))))
				.andExpect(status().isConflict());
	}

	@Test
	void listGenerationsReturns200WithItemsAndPaginationMetadata() throws Exception {
		UUID generationId = UUID.randomUUID();
		UUID planId = UUID.randomUUID();
		UUID startPeriodId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		GenerationSummary summary = new GenerationSummary(generationId, planId, startPeriodId, programId, 7, "2026-7",
				GenerationStatus.ACTIVE);
		when(listGenerationsUseCase
				.listGenerations(new ListGenerationsQuery(GenerationStatus.ACTIVE, "2026", programId, 0, 20)))
				.thenReturn(new ListGenerationsResult(List.of(summary), 1L, 1, 0, 20));

		mockMvc.perform(get("/generations").param("status", "ACTIVE").param("search", "2026")
				.param("programId", programId.toString())).andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(generationId.toString()))
				.andExpect(jsonPath("$.items[0].code").value("2026-7"))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void listGenerationsDefaultsPageAndSizeWhenOmitted() throws Exception {
		when(listGenerationsUseCase.listGenerations(new ListGenerationsQuery(null, null, null, 0, 20)))
				.thenReturn(new ListGenerationsResult(List.of(), 0L, 0, 0, 20));

		mockMvc.perform(get("/generations")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());

		verify(listGenerationsUseCase).listGenerations(new ListGenerationsQuery(null, null, null, 0, 20));
	}

	@Test
	void getGenerationReturns200WithBodyWhenFound() throws Exception {
		UUID generationId = UUID.randomUUID();
		when(getGenerationUseCase.getById(generationId)).thenReturn(new GenerationResult(generationId,
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 7, "2026-7", GenerationStatus.ACTIVE));

		mockMvc.perform(get("/generations/{id}", generationId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(generationId.toString()));
	}

	@Test
	void getGenerationReturns404WhenNotFound() throws Exception {
		UUID unknownId = UUID.randomUUID();
		when(getGenerationUseCase.getById(unknownId))
				.thenThrow(new GenerationNotFoundException("Generation not found: " + unknownId));

		mockMvc.perform(get("/generations/{id}", unknownId)).andExpect(status().isNotFound());
	}

	@Test
	void updateGenerationReturns200WithBody() throws Exception {
		UUID generationId = UUID.randomUUID();
		UUID planId = UUID.randomUUID();
		UUID startPeriodId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		when(updateGenerationUseCase.updateGeneration(new UpdateGenerationCommand(generationId, planId, startPeriodId, 8)))
				.thenReturn(new GenerationResult(generationId, planId, startPeriodId, programId, 8, "2027-8",
						GenerationStatus.ACTIVE));

		mockMvc.perform(put("/generations/{id}", generationId).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody(planId, startPeriodId, 8))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.number").value(8))
				.andExpect(jsonPath("$.code").value("2027-8"));
	}

	@Test
	void updateGenerationReturns404WhenNotFound() throws Exception {
		when(updateGenerationUseCase.updateGeneration(any()))
				.thenThrow(new GenerationNotFoundException("Generation not found: x"));

		mockMvc.perform(put("/generations/{id}", UUID.randomUUID()).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody(UUID.randomUUID(), UUID.randomUUID(), 8))))
				.andExpect(status().isNotFound());
	}

	@Test
	void updateGenerationWithDuplicateNumberReturns409() throws Exception {
		when(updateGenerationUseCase.updateGeneration(any()))
				.thenThrow(new DuplicateGenerationNumberException("Generation number already in use: 8"));

		mockMvc.perform(put("/generations/{id}", UUID.randomUUID()).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody(UUID.randomUUID(), UUID.randomUUID(), 8))))
				.andExpect(status().isConflict());
	}

	@Test
	void changeStatusReturns200WithUpdatedStatus() throws Exception {
		UUID generationId = UUID.randomUUID();
		when(changeGenerationStatusUseCase
				.changeStatus(new ChangeStatusCommand(callerId, generationId, GenerationStatus.FINISHED)))
				.thenReturn(new GenerationResult(generationId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
						7, "2026-7", GenerationStatus.FINISHED));

		mockMvc.perform(patch("/generations/" + generationId + "/status").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(GenerationStatus.FINISHED))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FINISHED"));

		verify(changeGenerationStatusUseCase)
				.changeStatus(new ChangeStatusCommand(callerId, generationId, GenerationStatus.FINISHED));
	}

	@Test
	void changeStatusOfUnknownGenerationReturns404() throws Exception {
		UUID generationId = UUID.randomUUID();
		when(changeGenerationStatusUseCase.changeStatus(any()))
				.thenThrow(new GenerationNotFoundException("Generation not found: " + generationId));

		mockMvc.perform(patch("/generations/" + generationId + "/status").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(GenerationStatus.FINISHED))))
				.andExpect(status().isNotFound());
	}

	private record CreateBody(UUID planId, UUID startPeriodId, int number) {
	}

	private record UpdateBody(UUID planId, UUID startPeriodId, int number) {
	}

	private record ChangeStatusBody(GenerationStatus status) {
	}
}
