package mx.edu.utez.sisa.identity.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.identity.domain.model.UserStatus;
import mx.edu.utez.sisa.identity.domain.port.in.AssignRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.AssignRoleUseCase.AssignRoleCommand;
import mx.edu.utez.sisa.identity.domain.port.in.AssignRoleUseCase.AssignRoleResult;
import mx.edu.utez.sisa.identity.domain.port.in.CreateUserUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.CreateUserUseCase.CreateUserCommand;
import mx.edu.utez.sisa.identity.domain.port.in.CreateUserUseCase.UserCreationResult;
import mx.edu.utez.sisa.identity.domain.port.in.ListUsersUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.ListUsersUseCase.ListUsersQuery;
import mx.edu.utez.sisa.identity.domain.port.in.ListUsersUseCase.ListUsersResult;
import mx.edu.utez.sisa.identity.domain.port.in.ListUsersUseCase.UserRoleSummary;
import mx.edu.utez.sisa.identity.domain.port.in.ListUsersUseCase.UserSummary;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.identity.shared.exception.DivisionRuleViolationException;
import mx.edu.utez.sisa.identity.shared.exception.MustChangePasswordException;
import mx.edu.utez.sisa.identity.shared.exception.PersonAlreadyHasUserException;
import mx.edu.utez.sisa.shared.model.RoleType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin-controller tests for task 5.6: {@code UserController} delegates to
 * {@code CreateUserUseCase}/{@code AssignRoleUseCase}, passing the caller id
 * extracted from the {@code SecurityContext}.
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private CreateUserUseCase createUserUseCase;

	@MockitoBean
	private AssignRoleUseCase assignRoleUseCase;

	@MockitoBean
	private ListUsersUseCase listUsersUseCase;

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
	void createUserReturns201WithUserIdAndMustChangePassword() throws Exception {
		UUID personId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(createUserUseCase.createUser(new CreateUserCommand(callerId, personId, "temp-pass")))
				.thenReturn(new UserCreationResult(userId, "jane.doe@utez.edu.mx", true));

		mockMvc.perform(post("/users").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateUserBody(personId, "temp-pass"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.userId").value(userId.toString()))
				.andExpect(jsonPath("$.username").value("jane.doe@utez.edu.mx"))
				.andExpect(jsonPath("$.mustChangePassword").value(true));
	}

	@Test
	void createUserForDuplicatePersonReturns409() throws Exception {
		when(createUserUseCase.createUser(any())).thenThrow(new PersonAlreadyHasUserException("already has user"));

		mockMvc.perform(post("/users").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateUserBody(UUID.randomUUID(), "temp-pass"))))
				.andExpect(status().isConflict());
	}

	@Test
	void createUserByMustChangePasswordCallerReturns403() throws Exception {
		when(createUserUseCase.createUser(any())).thenThrow(new MustChangePasswordException("must change"));

		mockMvc.perform(post("/users").contentType("application/json")
				.content(objectMapper.writeValueAsString(new CreateUserBody(UUID.randomUUID(), "temp-pass"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void assignRoleReturns201WithRoleAndDivision() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID divisionId = UUID.randomUUID();
		UUID userRoleId = UUID.randomUUID();
		when(assignRoleUseCase.assignRole(new AssignRoleCommand(callerId, userId, RoleType.DIRECTOR_DIVISION, divisionId)))
				.thenReturn(new AssignRoleResult(userRoleId, RoleType.DIRECTOR_DIVISION, divisionId));

		mockMvc.perform(post("/users/" + userId + "/roles").contentType("application/json")
				.content(objectMapper.writeValueAsString(new AssignRoleBody(RoleType.DIRECTOR_DIVISION, divisionId))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.userRoleId").value(userRoleId.toString()))
				.andExpect(jsonPath("$.roleType").value("DIRECTOR_DIVISION"))
				.andExpect(jsonPath("$.divisionId").value(divisionId.toString()));

		verify(assignRoleUseCase)
				.assignRole(new AssignRoleCommand(callerId, userId, RoleType.DIRECTOR_DIVISION, divisionId));
	}

	@Test
	void assignRoleViolatingDivisionRuleReturns400() throws Exception {
		when(assignRoleUseCase.assignRole(any()))
				.thenThrow(new DivisionRuleViolationException("Role GESTOR_ACADEMICO requires a divisionId"));

		mockMvc.perform(post("/users/" + UUID.randomUUID() + "/roles").contentType("application/json")
				.content(objectMapper.writeValueAsString(new AssignRoleBody(RoleType.GESTOR_ACADEMICO, null))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void listUsersReturns200WithItemsAndPaginationMetadata() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID personId = UUID.randomUUID();
		UUID divisionId = UUID.randomUUID();
		UserSummary summary = new UserSummary(userId, personId, "Ana García López", "ana.garcia@utez.edu.mx",
				List.of(new UserRoleSummary(RoleType.DIRECTOR_DIVISION, divisionId)), UserStatus.ACTIVE, null);
		when(listUsersUseCase.listUsers(new ListUsersQuery(callerId, RoleType.DIRECTOR_DIVISION, UserStatus.ACTIVE,
				"ana", 0, 20))).thenReturn(new ListUsersResult(List.of(summary), 1L, 1, 0, 20));

		mockMvc.perform(get("/users").param("role", "DIRECTOR_DIVISION").param("status", "ACTIVE")
				.param("search", "ana"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].userId").value(userId.toString()))
				.andExpect(jsonPath("$.items[0].fullName").value("Ana García López"))
				.andExpect(jsonPath("$.items[0].roles[0].roleType").value("DIRECTOR_DIVISION"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.totalPages").value(1))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void listUsersDefaultsPageAndSizeWhenOmitted() throws Exception {
		when(listUsersUseCase.listUsers(new ListUsersQuery(callerId, null, null, null, 0, 20)))
				.thenReturn(new ListUsersResult(List.of(), 0L, 0, 0, 20));

		mockMvc.perform(get("/users")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());

		verify(listUsersUseCase).listUsers(new ListUsersQuery(callerId, null, null, null, 0, 20));
	}

	@Test
	void listUsersByMustChangePasswordCallerReturns403() throws Exception {
		when(listUsersUseCase.listUsers(any())).thenThrow(new MustChangePasswordException("must change"));

		mockMvc.perform(get("/users")).andExpect(status().isForbidden());
	}

	@Test
	void listUsersWithInvalidRoleQueryParamReturns400() throws Exception {
		mockMvc.perform(get("/users").param("role", "NOT_A_ROLE")).andExpect(status().isBadRequest());
	}

	private record CreateUserBody(UUID personId, String temporaryPassword) {
	}

	private record AssignRoleBody(RoleType roleType, UUID divisionId) {
	}
}
