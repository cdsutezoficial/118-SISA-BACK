package mx.edu.utez.sisa.identity.infrastructure.web;

import jakarta.validation.Valid;
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
import mx.edu.utez.sisa.identity.domain.port.in.ListUsersUseCase.UserSummary;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.AssignRoleRequest;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.AssignRoleResponse;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.CreateUserRequest;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.CreateUserResponse;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.UserListItemResponse;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.UserListItemResponse.UserRoleItem;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.UserListResponse;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Thin controller for {@code POST /users}, {@code POST /users/{userId}/roles}
 * (design.md — REST endpoints, task 5.6), and {@code GET /users}
 * (01-identidad.md — ListUsersUseCase). The two POST endpoints are
 * ADMIN-only; {@code GET /users} additionally allows SERVICIOS_ESCOLARES —
 * both enforced by a more specific matcher in {@code SecurityFilterConfig}
 * declared before its blanket {@code /users/**} rule. The caller id used for
 * the mustChangePassword guard and authorization is extracted from the JWT
 * principal, not from the request body.
 */
@RestController
@RequestMapping("/users")
public class UserController {

	private final CreateUserUseCase createUserUseCase;
	private final AssignRoleUseCase assignRoleUseCase;
	private final ListUsersUseCase listUsersUseCase;

	public UserController(CreateUserUseCase createUserUseCase, AssignRoleUseCase assignRoleUseCase,
			ListUsersUseCase listUsersUseCase) {
		this.createUserUseCase = createUserUseCase;
		this.assignRoleUseCase = assignRoleUseCase;
		this.listUsersUseCase = listUsersUseCase;
	}

	@PostMapping
	public ResponseEntity<CreateUserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
		UUID callerId = AuthenticatedCaller.currentUserId();
		UserCreationResult result = createUserUseCase
				.createUser(new CreateUserCommand(callerId, request.personId(), request.temporaryPassword()));
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new CreateUserResponse(result.userId(), result.username(), result.mustChangePassword()));
	}

	@PostMapping("/{userId}/roles")
	public ResponseEntity<AssignRoleResponse> assignRole(@PathVariable UUID userId,
			@Valid @RequestBody AssignRoleRequest request) {
		UUID callerId = AuthenticatedCaller.currentUserId();
		AssignRoleResult result = assignRoleUseCase
				.assignRole(new AssignRoleCommand(callerId, userId, request.roleType(), request.divisionId()));
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new AssignRoleResponse(result.userRoleId(), result.roleType(), result.divisionId()));
	}

	@GetMapping
	public ResponseEntity<UserListResponse> listUsers(@RequestParam(required = false) RoleType role,
			@RequestParam(required = false) UserStatus status, @RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		UUID callerId = AuthenticatedCaller.currentUserId();
		ListUsersResult result = listUsersUseCase.listUsers(new ListUsersQuery(callerId, role, status, search, page, size));
		return ResponseEntity.ok(new UserListResponse(result.users().stream().map(UserController::toItem).toList(),
				result.totalElements(), result.totalPages(), result.page(), result.size()));
	}

	private static UserListItemResponse toItem(UserSummary summary) {
		return new UserListItemResponse(summary.userId(), summary.personId(), summary.fullName(), summary.username(),
				summary.roles().stream().map(role -> new UserRoleItem(role.roleType(), role.divisionId())).toList(),
				summary.status(), summary.lastLoginAt());
	}
}
