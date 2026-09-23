package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.DivisionStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicDivisionStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicDivisionStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase.AcademicDivisionResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase.CreateAcademicDivisionCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetAcademicDivisionUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicDivisionsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicDivisionsUseCase.DivisionSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicDivisionsUseCase.ListAcademicDivisionsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicDivisionsUseCase.ListAcademicDivisionsResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicDivisionUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicDivisionUseCase.UpdateAcademicDivisionCommand;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.AcademicDivisionJpaRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicDivisionNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DirectorNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateDivisionCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateDivisionNameException;
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
 * Thin-controller tests for {@link AcademicDivisionController}: delegates to
 * the 4 use cases, validates request bodies, and maps
 * {@code academic_config}'s exceptions to the expected HTTP status via
 * {@link GlobalExceptionHandler} (both this module's advice and
 * {@code identity}'s app-wide bean-validation advice are picked up by
 * {@code @WebMvcTest}'s classpath scan, mirroring {@code UserControllerTest}).
 */
@WebMvcTest(AcademicDivisionController.class)
@AutoConfigureMockMvc(addFilters = false)
class AcademicDivisionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private CreateAcademicDivisionUseCase createAcademicDivisionUseCase;

	@MockitoBean
	private UpdateAcademicDivisionUseCase updateAcademicDivisionUseCase;

	@MockitoBean
	private ListAcademicDivisionsUseCase listAcademicDivisionsUseCase;

	@MockitoBean
	private ChangeAcademicDivisionStatusUseCase changeAcademicDivisionStatusUseCase;

	@MockitoBean
	private GetAcademicDivisionUseCase getAcademicDivisionUseCase;

	@MockitoBean
	private AcademicDivisionJpaRepository academicDivisionJpaRepository;

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
	void createDivisionReturns201WithBody() throws Exception {
		UUID divisionId = UUID.randomUUID();
		UUID directorId = UUID.randomUUID();
		when(createAcademicDivisionUseCase.createDivision(
				new CreateAcademicDivisionCommand("Ingeniería en Software", "ISW", "desc", directorId)))
				.thenReturn(new AcademicDivisionResult(divisionId, "Ingeniería en Software", "ISW", "desc",
						directorId, DivisionStatus.ACTIVE));

		mockMvc.perform(post("/divisions").contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreateDivisionBody("Ingeniería en Software", "ISW", "desc", directorId))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(divisionId.toString()))
				.andExpect(jsonPath("$.name").value("Ingeniería en Software"))
				.andExpect(jsonPath("$.code").value("ISW"))
				.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void createDivisionWithBlankNameReturns400() throws Exception {
		mockMvc.perform(post("/divisions").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateDivisionBody("", "ISW", "desc", null))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createDivisionWithDuplicateCodeReturns409() throws Exception {
		when(createAcademicDivisionUseCase.createDivision(any()))
				.thenThrow(new DuplicateDivisionCodeException("Division code already in use: ISW"));

		mockMvc.perform(post("/divisions").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateDivisionBody("Name", "ISW", "desc", null))))
				.andExpect(status().isConflict());
	}

	@Test
	void createDivisionWithDuplicateNameReturns409() throws Exception {
		when(createAcademicDivisionUseCase.createDivision(any()))
				.thenThrow(new DuplicateDivisionNameException("Division name already in use: Name"));

		mockMvc.perform(post("/divisions").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateDivisionBody("Name", "ISW", "desc", null))))
				.andExpect(status().isConflict());
	}

	@Test
	void createDivisionWithUnknownDirectorReturns400() throws Exception {
		UUID directorId = UUID.randomUUID();
		when(createAcademicDivisionUseCase.createDivision(any()))
				.thenThrow(new DirectorNotFoundException("Director person not found: " + directorId));

		mockMvc.perform(post("/divisions").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateDivisionBody("Name", "ISW", "desc", directorId))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void updateDivisionReturns200WithBody() throws Exception {
		UUID divisionId = UUID.randomUUID();
		when(updateAcademicDivisionUseCase
				.updateDivision(new UpdateAcademicDivisionCommand(divisionId, "New Name", "NEW", "desc", null)))
				.thenReturn(new AcademicDivisionResult(divisionId, "New Name", "NEW", "desc", null,
						DivisionStatus.ACTIVE));

		mockMvc.perform(put("/divisions/" + divisionId).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateDivisionBody("New Name", "NEW", "desc", null))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("New Name"))
				.andExpect(jsonPath("$.code").value("NEW"));
	}

	@Test
	void updateUnknownDivisionReturns404() throws Exception {
		UUID divisionId = UUID.randomUUID();
		when(updateAcademicDivisionUseCase.updateDivision(any()))
				.thenThrow(new AcademicDivisionNotFoundException("Academic division not found: " + divisionId));

		mockMvc.perform(put("/divisions/" + divisionId).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateDivisionBody("New Name", "NEW", "desc", null))))
				.andExpect(status().isNotFound());
	}

	@Test
	void getDivisionReturns200WithBody() throws Exception {
		UUID divisionId = UUID.randomUUID();
		when(getAcademicDivisionUseCase.getById(divisionId)).thenReturn(
				new AcademicDivisionResult(divisionId, "Ingeniería en Software", "ISW", "desc", null,
						DivisionStatus.ACTIVE));

		mockMvc.perform(get("/divisions/" + divisionId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(divisionId.toString()))
				.andExpect(jsonPath("$.name").value("Ingeniería en Software"))
				.andExpect(jsonPath("$.code").value("ISW"));
	}

	@Test
	void getUnknownDivisionReturns404() throws Exception {
		UUID divisionId = UUID.randomUUID();
		when(getAcademicDivisionUseCase.getById(divisionId))
				.thenThrow(new AcademicDivisionNotFoundException("Academic division not found: " + divisionId));

		mockMvc.perform(get("/divisions/" + divisionId)).andExpect(status().isNotFound());
	}

	@Test
	void listDivisionsReturns200WithItemsAndPaginationMetadata() throws Exception {
		UUID divisionId = UUID.randomUUID();
		DivisionSummary summary = new DivisionSummary(divisionId, "Ingeniería en Software", "ISW", "desc", null,
				DivisionStatus.ACTIVE, 0);
		when(listAcademicDivisionsUseCase
				.listDivisions(new ListAcademicDivisionsQuery(DivisionStatus.ACTIVE, "software", 0, 20)))
				.thenReturn(new ListAcademicDivisionsResult(List.of(summary), 1L, 1, 0, 20));

		mockMvc.perform(get("/divisions").param("status", "ACTIVE").param("search", "software"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(divisionId.toString()))
				.andExpect(jsonPath("$.items[0].programCount").value(0))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.totalPages").value(1))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void listDivisionsDefaultsPageAndSizeWhenOmitted() throws Exception {
		when(listAcademicDivisionsUseCase.listDivisions(new ListAcademicDivisionsQuery(null, null, 0, 20)))
				.thenReturn(new ListAcademicDivisionsResult(List.of(), 0L, 0, 0, 20));

		mockMvc.perform(get("/divisions")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());

		verify(listAcademicDivisionsUseCase).listDivisions(new ListAcademicDivisionsQuery(null, null, 0, 20));
	}

	@Test
	void listDivisionsWithInvalidStatusQueryParamReturns400() throws Exception {
		mockMvc.perform(get("/divisions").param("status", "NOT_A_STATUS")).andExpect(status().isBadRequest());
	}

	@Test
	void changeStatusReturns200WithUpdatedStatus() throws Exception {
		UUID divisionId = UUID.randomUUID();
		when(changeAcademicDivisionStatusUseCase
				.changeStatus(new ChangeStatusCommand(callerId, divisionId, DivisionStatus.INACTIVE)))
				.thenReturn(new AcademicDivisionResult(divisionId, "Name", "COD", "desc", null,
						DivisionStatus.INACTIVE));

		mockMvc.perform(patch("/divisions/" + divisionId + "/status").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(DivisionStatus.INACTIVE))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("INACTIVE"));

		verify(changeAcademicDivisionStatusUseCase)
				.changeStatus(new ChangeStatusCommand(callerId, divisionId, DivisionStatus.INACTIVE));
	}

	@Test
	void changeStatusOfUnknownDivisionReturns404() throws Exception {
		UUID divisionId = UUID.randomUUID();
		when(changeAcademicDivisionStatusUseCase.changeStatus(any()))
				.thenThrow(new AcademicDivisionNotFoundException("Academic division not found: " + divisionId));

		mockMvc.perform(patch("/divisions/" + divisionId + "/status").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(DivisionStatus.ACTIVE))))
				.andExpect(status().isNotFound());
	}

	@Test
	void listDivisionOptionsReturnsOnlyActiveDivisionsWithMinimalProjection() throws Exception {
		UUID divisionId = UUID.randomUUID();
		AcademicDivisionJpaRepository.DivisionOptionProjection active = mock(
				AcademicDivisionJpaRepository.DivisionOptionProjection.class);
		when(active.getId()).thenReturn(divisionId);
		when(active.getName()).thenReturn("Ingeniería en Software");
		when(active.getCode()).thenReturn("ISW");
		when(academicDivisionJpaRepository.findByStatusOrderByNameAsc(DivisionStatus.ACTIVE))
				.thenReturn(List.of(active));

		mockMvc.perform(get("/divisions/options")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(divisionId.toString()))
				.andExpect(jsonPath("$[0].label").value("Ingeniería en Software"))
				.andExpect(jsonPath("$[0].code").value("ISW"))
				.andExpect(jsonPath("$[1]").doesNotExist());

		verify(academicDivisionJpaRepository).findByStatusOrderByNameAsc(DivisionStatus.ACTIVE);
	}

	private record CreateDivisionBody(String name, String code, String description, UUID directorPersonId) {
	}

	private record UpdateDivisionBody(String name, String code, String description, UUID directorPersonId) {
	}

	private record ChangeStatusBody(DivisionStatus status) {
	}
}
