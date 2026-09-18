package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicProgramStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicProgramStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase.AcademicProgramResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase.CreateAcademicProgramCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetAcademicProgramUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicProgramsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicProgramsUseCase.ListAcademicProgramsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicProgramsUseCase.ListAcademicProgramsResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicProgramsUseCase.ProgramSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicProgramUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicProgramUseCase.UpdateAcademicProgramCommand;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.AcademicProgramJpaRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicProgramNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DivisionNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateOfferNameModalityException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateProgramCodeException;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin-controller tests for {@link AcademicProgramController}: delegates to
 * the 5 use cases, validates request bodies, and maps
 * {@code academic_config}'s program exceptions to the expected HTTP status
 * via {@link GlobalExceptionHandler} — mirrors
 * {@code AcademicDivisionControllerTest}.
 */
@WebMvcTest(AcademicProgramController.class)
@AutoConfigureMockMvc(addFilters = false)
class AcademicProgramControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private CreateAcademicProgramUseCase createAcademicProgramUseCase;

	@MockitoBean
	private UpdateAcademicProgramUseCase updateAcademicProgramUseCase;

	@MockitoBean
	private ListAcademicProgramsUseCase listAcademicProgramsUseCase;

	@MockitoBean
	private GetAcademicProgramUseCase getAcademicProgramUseCase;

	@MockitoBean
	private ChangeAcademicProgramStatusUseCase changeAcademicProgramStatusUseCase;

	@MockitoBean
	private AcademicProgramJpaRepository academicProgramJpaRepository;

	@MockitoBean
	private JwtService jwtService;

	private UUID callerId;

	private UUID divisionId;

	@BeforeEach
	void setUp() {
		callerId = UUID.randomUUID();
		divisionId = UUID.randomUUID();
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				callerId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void createProgramReturns201WithBody() throws Exception {
		UUID programId = UUID.randomUUID();
		when(createAcademicProgramUseCase.createProgram(new CreateAcademicProgramCommand(divisionId,
				"Ingeniería en Software", "Ingeniería en Software", "ISC-01", AcademicLevel.INGENIERIA,
				ProgramModality.PRESENCIAL, null, "desc", null)))
				.thenReturn(new AcademicProgramResult(programId, divisionId, "Ingeniería en Software",
						"Ingeniería en Software", "ISC-01", AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null,
						"desc", null, ProgramStatus.ACTIVE));

		mockMvc.perform(post("/programs").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateProgramBody(divisionId, "Ingeniería en Software",
						"Ingeniería en Software", "ISC-01", AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null,
						"desc"))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(programId.toString()))
				.andExpect(jsonPath("$.code").value("ISC-01")).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void createProgramWithBlankCodeReturns400() throws Exception {
		mockMvc.perform(post("/programs").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateProgramBody(divisionId, "Ingeniería en Software",
						"Ingeniería en Software", "", AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, "desc"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createProgramWithMissingDivisionIdReturns400() throws Exception {
		mockMvc.perform(post("/programs").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateProgramBody(null, "Ingeniería en Software",
						"Ingeniería en Software", "ISC-01", AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null,
						"desc"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createProgramWithNonExistentDivisionReturns400() throws Exception {
		when(createAcademicProgramUseCase.createProgram(any()))
				.thenThrow(new DivisionNotFoundException("Academic division not found: " + divisionId));

		mockMvc.perform(post("/programs").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateProgramBody(divisionId, "Ingeniería en Software",
						"Ingeniería en Software", "ISC-01", AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null,
						"desc"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createProgramWithDuplicateCodeReturns409() throws Exception {
		when(createAcademicProgramUseCase.createProgram(any()))
				.thenThrow(new DuplicateProgramCodeException("Program code already in use: ISC-01"));

		mockMvc.perform(post("/programs").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateProgramBody(divisionId, "Ingeniería en Software",
						"Ingeniería en Software", "ISC-01", AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null,
						"desc"))))
				.andExpect(status().isConflict());
	}

	@Test
	void createProgramWithDuplicateOfferNameAndModalityReturns409() throws Exception {
		when(createAcademicProgramUseCase.createProgram(any())).thenThrow(
				new DuplicateOfferNameModalityException("Program offerName+modality already in use"));

		mockMvc.perform(post("/programs").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateProgramBody(divisionId, "Ingeniería en Software",
						"Ingeniería en Software", "ISC-01", AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null,
						"desc"))))
				.andExpect(status().isConflict());
	}

	@Test
	void updateProgramReturns200WithBody() throws Exception {
		UUID programId = UUID.randomUUID();
		when(updateAcademicProgramUseCase.updateProgram(new UpdateAcademicProgramCommand(programId, divisionId,
				"New Name", "New Offer", "ISC-02", AcademicLevel.LICENCIATURA, ProgramModality.MIXTA, null, "desc",
				null)))
				.thenReturn(new AcademicProgramResult(programId, divisionId, "New Name", "New Offer", "ISC-02",
						AcademicLevel.LICENCIATURA, ProgramModality.MIXTA, null, "desc", null, ProgramStatus.ACTIVE));

		mockMvc.perform(put("/programs/" + programId).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateProgramBody(divisionId, "New Name", "New Offer",
						"ISC-02", AcademicLevel.LICENCIATURA, ProgramModality.MIXTA, null, "desc"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("New Name"))
				.andExpect(jsonPath("$.code").value("ISC-02"));
	}

	@Test
	void updateUnknownProgramReturns404() throws Exception {
		UUID programId = UUID.randomUUID();
		when(updateAcademicProgramUseCase.updateProgram(any()))
				.thenThrow(new AcademicProgramNotFoundException("Academic program not found: " + programId));

		mockMvc.perform(put("/programs/" + programId).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateProgramBody(divisionId, "New Name", "New Offer",
						"ISC-02", AcademicLevel.LICENCIATURA, ProgramModality.MIXTA, null, "desc"))))
				.andExpect(status().isNotFound());
	}

	@Test
	void getProgramReturns200WithBody() throws Exception {
		UUID programId = UUID.randomUUID();
		when(getAcademicProgramUseCase.getById(programId))
				.thenReturn(new AcademicProgramResult(programId, divisionId, "Ingeniería en Software",
						"Ingeniería en Software", "ISC-01", AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null,
						"desc", null, ProgramStatus.ACTIVE));

		mockMvc.perform(get("/programs/" + programId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(programId.toString()))
				.andExpect(jsonPath("$.code").value("ISC-01"));
	}

	@Test
	void getUnknownProgramReturns404() throws Exception {
		UUID programId = UUID.randomUUID();
		when(getAcademicProgramUseCase.getById(programId))
				.thenThrow(new AcademicProgramNotFoundException("Academic program not found: " + programId));

		mockMvc.perform(get("/programs/" + programId)).andExpect(status().isNotFound());
	}

	@Test
	void listProgramsReturns200WithItemsAndPaginationMetadata() throws Exception {
		UUID programId = UUID.randomUUID();
		ProgramSummary summary = new ProgramSummary(programId, divisionId, "Ingeniería en Software",
				"Ingeniería en Software", "ISC-01", AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, "desc",
				null, ProgramStatus.ACTIVE);
		when(listAcademicProgramsUseCase
				.listPrograms(new ListAcademicProgramsQuery(ProgramStatus.ACTIVE, "software", divisionId, 0, 20)))
				.thenReturn(new ListAcademicProgramsResult(List.of(summary), 1L, 1, 0, 20));

		mockMvc.perform(get("/programs").param("status", "ACTIVE").param("search", "software")
				.param("divisionId", divisionId.toString())).andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(programId.toString()))
				.andExpect(jsonPath("$.items[0].divisionId").value(divisionId.toString()))
				.andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.totalPages").value(1))
				.andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void listProgramsDefaultsPageAndSizeWhenOmitted() throws Exception {
		when(listAcademicProgramsUseCase.listPrograms(new ListAcademicProgramsQuery(null, null, null, 0, 20)))
				.thenReturn(new ListAcademicProgramsResult(List.of(), 0L, 0, 0, 20));

		mockMvc.perform(get("/programs")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());

		verify(listAcademicProgramsUseCase).listPrograms(new ListAcademicProgramsQuery(null, null, null, 0, 20));
	}

	@Test
	void listProgramsWithInvalidStatusQueryParamReturns400() throws Exception {
		mockMvc.perform(get("/programs").param("status", "NOT_A_STATUS")).andExpect(status().isBadRequest());
	}

	@Test
	void changeStatusReturns200WithUpdatedStatus() throws Exception {
		UUID programId = UUID.randomUUID();
		when(changeAcademicProgramStatusUseCase
				.changeStatus(new ChangeStatusCommand(callerId, programId, ProgramStatus.INACTIVE)))
				.thenReturn(new AcademicProgramResult(programId, divisionId, "Name", "Offer", "COD",
						AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, "desc", null,
						ProgramStatus.INACTIVE));

		mockMvc.perform(patch("/programs/" + programId + "/status").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(ProgramStatus.INACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));

		verify(changeAcademicProgramStatusUseCase)
				.changeStatus(new ChangeStatusCommand(callerId, programId, ProgramStatus.INACTIVE));
	}

	@Test
	void changeStatusOfUnknownProgramReturns404() throws Exception {
		UUID programId = UUID.randomUUID();
		when(changeAcademicProgramStatusUseCase.changeStatus(any()))
				.thenThrow(new AcademicProgramNotFoundException("Academic program not found: " + programId));

		mockMvc.perform(patch("/programs/" + programId + "/status").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(ProgramStatus.ACTIVE))))
				.andExpect(status().isNotFound());
	}

	@Test
	void listProgramOptionsReturnsOnlyActiveProgramsWithMinimalProjection() throws Exception {
		UUID programId = UUID.randomUUID();
		AcademicProgramJpaRepository.ProgramOptionProjection active = mock(
				AcademicProgramJpaRepository.ProgramOptionProjection.class);
		when(active.getId()).thenReturn(programId);
		when(active.getName()).thenReturn("Ingeniería en Software");
		when(active.getCode()).thenReturn("ISW");
		when(academicProgramJpaRepository.findByStatusOrderByNameAsc(ProgramStatus.ACTIVE))
				.thenReturn(List.of(active));

		mockMvc.perform(get("/programs/options")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(programId.toString()))
				.andExpect(jsonPath("$[0].label").value("Ingeniería en Software"))
				.andExpect(jsonPath("$[0].code").value("ISW"))
				.andExpect(jsonPath("$[1]").doesNotExist());

		verify(academicProgramJpaRepository).findByStatusOrderByNameAsc(ProgramStatus.ACTIVE);
	}

	@Test
	void listProgramOptionsFiltersByDivision() throws Exception {
		UUID divisionId = UUID.randomUUID();
		when(academicProgramJpaRepository.findByStatusAndDivisionIdOrderByNameAsc(ProgramStatus.ACTIVE, divisionId))
				.thenReturn(List.of());

		mockMvc.perform(get("/programs/options").param("divisionId", divisionId.toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

		verify(academicProgramJpaRepository)
				.findByStatusAndDivisionIdOrderByNameAsc(ProgramStatus.ACTIVE, divisionId);
	}

	private record CreateProgramBody(UUID divisionId, String name, String offerName, String code, AcademicLevel level,
			ProgramModality modality, UUID continuityProgramId, String description) {
	}

	private record UpdateProgramBody(UUID divisionId, String name, String offerName, String code, AcademicLevel level,
			ProgramModality modality, UUID continuityProgramId, String description) {
	}

	private record ChangeStatusBody(ProgramStatus status) {
	}
}
