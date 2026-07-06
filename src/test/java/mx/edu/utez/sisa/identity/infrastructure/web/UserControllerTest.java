package mx.edu.utez.sisa.identity.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.identity.domain.port.in.AssignRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.AssignRoleUseCase.AssignRoleCommand;
import mx.edu.utez.sisa.identity.domain.port.in.AssignRoleUseCase.AssignRoleResult;
import mx.edu.utez.sisa.identity.domain.port.in.CreateUserUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.CreateUserUseCase.CreateUserCommand;
import mx.edu.utez.sisa.identity.domain.port.in.CreateUserUseCase.UserCreationResult;
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

	private record CreateUserBody(UUID personId, String temporaryPassword) {
	}

	private record AssignRoleBody(RoleType roleType, UUID divisionId) {
	}
}
