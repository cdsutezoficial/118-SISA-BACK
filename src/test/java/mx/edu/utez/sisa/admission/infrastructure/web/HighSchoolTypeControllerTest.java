package mx.edu.utez.sisa.admission.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.admission.domain.model.HighSchoolTypeStatus;
import mx.edu.utez.sisa.admission.domain.port.in.ChangeHighSchoolTypeStatusUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ChangeHighSchoolTypeStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase.CreateHighSchoolTypeCommand;
import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase.HighSchoolTypeResult;
import mx.edu.utez.sisa.admission.domain.port.in.GetHighSchoolTypeUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ListHighSchoolTypesUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ListHighSchoolTypesUseCase.ListHighSchoolTypesQuery;
import mx.edu.utez.sisa.admission.domain.port.in.ListHighSchoolTypesUseCase.ListHighSchoolTypesResult;
import mx.edu.utez.sisa.admission.domain.port.in.ListHighSchoolTypesUseCase.HighSchoolTypeSummary;
import mx.edu.utez.sisa.admission.domain.port.in.UpdateHighSchoolTypeUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.UpdateHighSchoolTypeUseCase.UpdateHighSchoolTypeCommand;
import mx.edu.utez.sisa.admission.shared.exception.HighSchoolTypeNotFoundException;
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
 * Thin-controller tests for {@link HighSchoolTypeController}, mirroring
 * {@code OutreachChannelControllerTest}'s style.
 */
@WebMvcTest(HighSchoolTypeController.class)
@AutoConfigureMockMvc(addFilters = false)
class HighSchoolTypeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ListHighSchoolTypesUseCase listHighSchoolTypesUseCase;

	@MockitoBean
	private CreateHighSchoolTypeUseCase createHighSchoolTypeUseCase;

	@MockitoBean
	private GetHighSchoolTypeUseCase getHighSchoolTypeUseCase;

	@MockitoBean
	private UpdateHighSchoolTypeUseCase updateHighSchoolTypeUseCase;

	@MockitoBean
	private ChangeHighSchoolTypeStatusUseCase changeHighSchoolTypeStatusUseCase;

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
	void createHighSchoolTypeReturns201WithBody() throws Exception {
		UUID typeId = UUID.randomUUID();
		when(createHighSchoolTypeUseCase.createHighSchoolType(new CreateHighSchoolTypeCommand("Conalep")))
				.thenReturn(new HighSchoolTypeResult(typeId, "Conalep", HighSchoolTypeStatus.ACTIVE));

		mockMvc.perform(post("/high-school-types").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody("Conalep")))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(typeId.toString())).andExpect(jsonPath("$.name").value("Conalep"))
				.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void createHighSchoolTypeWithBlankNameReturns400() throws Exception {
		mockMvc.perform(post("/high-school-types").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateBody("")))).andExpect(status().isBadRequest());
	}

	@Test
	void listHighSchoolTypesReturns200WithItemsAndPaginationMetadata() throws Exception {
		UUID typeId = UUID.randomUUID();
		HighSchoolTypeSummary summary = new HighSchoolTypeSummary(typeId, "Conalep", HighSchoolTypeStatus.ACTIVE);
		when(listHighSchoolTypesUseCase
				.listHighSchoolTypes(new ListHighSchoolTypesQuery(HighSchoolTypeStatus.ACTIVE, "cona", 0, 20)))
				.thenReturn(new ListHighSchoolTypesResult(List.of(summary), 1L, 1, 0, 20));

		mockMvc.perform(get("/high-school-types").param("status", "ACTIVE").param("search", "cona"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(typeId.toString()))
				.andExpect(jsonPath("$.items[0].name").value("Conalep"))
				.andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.totalPages").value(1))
				.andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void listHighSchoolTypesDefaultsPageAndSizeWhenOmitted() throws Exception {
		when(listHighSchoolTypesUseCase.listHighSchoolTypes(new ListHighSchoolTypesQuery(null, null, 0, 20)))
				.thenReturn(new ListHighSchoolTypesResult(List.of(), 0L, 0, 0, 20));

		mockMvc.perform(get("/high-school-types")).andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty());

		verify(listHighSchoolTypesUseCase).listHighSchoolTypes(new ListHighSchoolTypesQuery(null, null, 0, 20));
	}

	@Test
	void listHighSchoolTypesWithInvalidStatusQueryParamReturns400() throws Exception {
		mockMvc.perform(get("/high-school-types").param("status", "NOT_A_STATUS")).andExpect(status().isBadRequest());
	}

	@Test
	void getHighSchoolTypeReturns200WithBodyWhenFound() throws Exception {
		UUID typeId = UUID.randomUUID();
		when(getHighSchoolTypeUseCase.getById(typeId))
				.thenReturn(new HighSchoolTypeResult(typeId, "Conalep", HighSchoolTypeStatus.ACTIVE));

		mockMvc.perform(get("/high-school-types/{id}", typeId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(typeId.toString())).andExpect(jsonPath("$.name").value("Conalep"));
	}

	@Test
	void getHighSchoolTypeReturns404WhenNotFound() throws Exception {
		UUID unknownId = UUID.randomUUID();
		when(getHighSchoolTypeUseCase.getById(unknownId))
				.thenThrow(new HighSchoolTypeNotFoundException("High school type not found: " + unknownId));

		mockMvc.perform(get("/high-school-types/{id}", unknownId)).andExpect(status().isNotFound());
	}

	@Test
	void updateHighSchoolTypeReturns200WithBody() throws Exception {
		UUID typeId = UUID.randomUUID();
		when(updateHighSchoolTypeUseCase.updateHighSchoolType(new UpdateHighSchoolTypeCommand(typeId, "Cobaem")))
				.thenReturn(new HighSchoolTypeResult(typeId, "Cobaem", HighSchoolTypeStatus.ACTIVE));

		mockMvc.perform(put("/high-school-types/{id}", typeId).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody("Cobaem")))).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Cobaem"));
	}

	@Test
	void updateHighSchoolTypeWithBlankNameReturns400() throws Exception {
		mockMvc.perform(put("/high-school-types/{id}", UUID.randomUUID()).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody("")))).andExpect(status().isBadRequest());
	}

	@Test
	void updateHighSchoolTypeReturns404WhenNotFound() throws Exception {
		UUID unknownId = UUID.randomUUID();
		when(updateHighSchoolTypeUseCase.updateHighSchoolType(any()))
				.thenThrow(new HighSchoolTypeNotFoundException("High school type not found: " + unknownId));

		mockMvc.perform(put("/high-school-types/{id}", unknownId).contentType("application/json")
				.content(objectMapper.writeValueAsString(new UpdateBody("Cobaem")))).andExpect(status().isNotFound());
	}

	@Test
	void changeStatusReturns200WithUpdatedStatus() throws Exception {
		UUID typeId = UUID.randomUUID();
		when(changeHighSchoolTypeStatusUseCase
				.changeStatus(new ChangeStatusCommand(callerId, typeId, HighSchoolTypeStatus.INACTIVE)))
				.thenReturn(new HighSchoolTypeResult(typeId, "Conalep", HighSchoolTypeStatus.INACTIVE));

		mockMvc.perform(patch("/high-school-types/" + typeId + "/status").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(HighSchoolTypeStatus.INACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));

		verify(changeHighSchoolTypeStatusUseCase)
				.changeStatus(new ChangeStatusCommand(callerId, typeId, HighSchoolTypeStatus.INACTIVE));
	}

	@Test
	void changeStatusOfUnknownTypeReturns404() throws Exception {
		UUID typeId = UUID.randomUUID();
		when(changeHighSchoolTypeStatusUseCase.changeStatus(any()))
				.thenThrow(new HighSchoolTypeNotFoundException("High school type not found: " + typeId));

		mockMvc.perform(patch("/high-school-types/" + typeId + "/status").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(HighSchoolTypeStatus.ACTIVE))))
				.andExpect(status().isNotFound());
	}

	private record CreateBody(String name) {
	}

	private record UpdateBody(String name) {
	}

	private record ChangeStatusBody(HighSchoolTypeStatus status) {
	}
}
