package mx.edu.utez.sisa.identity.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.identity.domain.port.in.CreatePersonUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.CreatePersonUseCase.CreatePersonCommand;
import mx.edu.utez.sisa.identity.domain.port.in.CreatePersonUseCase.PersonResult;
import mx.edu.utez.sisa.identity.domain.port.in.ListPersonsUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.ListPersonsUseCase.ListPersonsQuery;
import mx.edu.utez.sisa.identity.domain.port.in.ListPersonsUseCase.ListPersonsResult;
import mx.edu.utez.sisa.identity.domain.port.in.ListPersonsUseCase.PersonSummary;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.identity.infrastructure.security.PermissionCache;
import mx.edu.utez.sisa.identity.shared.exception.DuplicateCurpException;
import mx.edu.utez.sisa.identity.shared.exception.DuplicateInstitutionalEmailException;
import mx.edu.utez.sisa.identity.shared.exception.MustChangePasswordException;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin-controller tests for {@code PersonController} (plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.1/4.2),
 * mirroring {@code UserControllerTest}'s style.
 */
@WebMvcTest(PersonController.class)
@AutoConfigureMockMvc(addFilters = false)
class PersonControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private CreatePersonUseCase createPersonUseCase;

	@MockitoBean
	private ListPersonsUseCase listPersonsUseCase;

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
	void createPersonReturns201WithPersonData() throws Exception {
		UUID personId = UUID.randomUUID();
		when(createPersonUseCase.createPerson(new CreatePersonCommand(callerId, "CURP000000HDFRRN01", "Jane", "Doe",
				null, "jane.doe@utez.edu.mx")))
				.thenReturn(new PersonResult(personId, "CURP000000HDFRRN01", "Jane", "Doe", null, "jane.doe@utez.edu.mx"));

		mockMvc.perform(post("/persons").contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreatePersonBody("CURP000000HDFRRN01", "Jane", "Doe", null, "jane.doe@utez.edu.mx"))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(personId.toString()))
				.andExpect(jsonPath("$.curp").value("CURP000000HDFRRN01"))
				.andExpect(jsonPath("$.institutionalEmail").value("jane.doe@utez.edu.mx"));
	}

	@Test
	void createPersonWithDuplicateCurpReturns409() throws Exception {
		when(createPersonUseCase.createPerson(any())).thenThrow(new DuplicateCurpException("duplicate curp"));

		mockMvc.perform(post("/persons").contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreatePersonBody("CURP000000HDFRRN01", "Jane", "Doe", null, "jane.doe@utez.edu.mx"))))
				.andExpect(status().isConflict());
	}

	@Test
	void createPersonWithDuplicateInstitutionalEmailReturns409() throws Exception {
		when(createPersonUseCase.createPerson(any()))
				.thenThrow(new DuplicateInstitutionalEmailException("duplicate email"));

		mockMvc.perform(post("/persons").contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreatePersonBody("CURP000000HDFRRN02", "Jane", "Doe", null, "jane.doe@utez.edu.mx"))))
				.andExpect(status().isConflict());
	}

	@Test
	void createPersonWithoutInstitutionalEmailReturns400() throws Exception {
		mockMvc.perform(post("/persons").contentType("application/json")
				.content(objectMapper
						.writeValueAsString(new CreatePersonBody("CURP000000HDFRRN03", "Jane", "Doe", null, ""))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createPersonByMustChangePasswordCallerReturns403() throws Exception {
		when(createPersonUseCase.createPerson(any())).thenThrow(new MustChangePasswordException("must change"));

		mockMvc.perform(post("/persons").contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreatePersonBody("CURP000000HDFRRN04", "Jane", "Doe", null, "jane.doe4@utez.edu.mx"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void listPersonsReturns200WithItemsAndPaginationMetadata() throws Exception {
		UUID personId = UUID.randomUUID();
		PersonSummary summary = new PersonSummary(personId, "CURP123456789012", "Ana", "García", "López",
				"ana.garcia@utez.edu.mx", true);
		when(listPersonsUseCase.listPersons(new ListPersonsQuery(callerId, "ana", 0, 20)))
				.thenReturn(new ListPersonsResult(List.of(summary), 1L, 1, 0, 20));

		mockMvc.perform(get("/persons").param("search", "ana")).andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(personId.toString()))
				.andExpect(jsonPath("$.items[0].hasUser").value(true)).andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void listPersonsDefaultsPageAndSizeWhenOmitted() throws Exception {
		when(listPersonsUseCase.listPersons(new ListPersonsQuery(callerId, null, 0, 20)))
				.thenReturn(new ListPersonsResult(List.of(), 0L, 0, 0, 20));

		mockMvc.perform(get("/persons")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
	}

	@Test
	void listPersonsByMustChangePasswordCallerReturns403() throws Exception {
		when(listPersonsUseCase.listPersons(any())).thenThrow(new MustChangePasswordException("must change"));

		mockMvc.perform(get("/persons")).andExpect(status().isForbidden());
	}

	private record CreatePersonBody(String curp, String firstName, String lastName1, String lastName2,
			String institutionalEmail) {
	}
}
