package mx.edu.utez.sisa.identity.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.identity.domain.model.UserStatus;
import mx.edu.utez.sisa.identity.domain.port.in.AssignRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.AssignRoleUseCase.AssignRoleCommand;
import mx.edu.utez.sisa.identity.domain.port.in.AssignRoleUseCase.AssignRoleResult;
import mx.edu.utez.sisa.identity.domain.port.in.CreateUserUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.CreateUserUseCase.CreateUserCommand;
import mx.edu.utez.sisa.identity.domain.port.in.CreateUserUseCase.UserCreationResult;
import mx.edu.utez.sisa.identity.domain.port.in.GetUserUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.GetUserUseCase.GetUserQuery;
import mx.edu.utez.sisa.identity.domain.port.in.GetUserUseCase.UserDetailResult;
import mx.edu.utez.sisa.identity.domain.port.in.ListUsersUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.ListUsersUseCase.ListUsersQuery;
import mx.edu.utez.sisa.identity.domain.port.in.ListUsersUseCase.ListUsersResult;
import mx.edu.utez.sisa.identity.domain.port.in.ListUsersUseCase.UserSummary;
import mx.edu.utez.sisa.identity.domain.port.in.RevokeRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.RevokeRoleUseCase.RevokeRoleCommand;
import mx.edu.utez.sisa.identity.domain.port.in.UnlockUserUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.UnlockUserUseCase.UnlockUserCommand;
import mx.edu.utez.sisa.identity.domain.port.in.UnlockUserUseCase.UnlockUserResult;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.AssignRoleRequest;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.AssignRoleResponse;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.CreateUserRequest;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.CreateUserResponse;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.UnlockUserResponse;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.UserDetailResponse;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.UserDetailResponse.UserRoleDetailItem;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.UserListItemResponse;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.UserListItemResponse.UserRoleItem;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.UserListResponse;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Thin controller for {@code POST /users}, {@code POST /users/{userId}/roles}
 * (design.md — REST endpoints, task 5.6), {@code GET /users}
 * (01-identidad.md — ListUsersUseCase), and — plan
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} —
 * {@code GET /users/{id}}, {@code DELETE /users/{userId}/roles/{userRoleId}},
 * and {@code PATCH /users/{id}/unlock}. {@code POST /users},
 * {@code POST /users/{userId}/roles}, {@code DELETE .../roles/{userRoleId}}
 * and {@code PATCH /users/{id}/unlock} are ADMIN-only; both GET endpoints
 * additionally allow SERVICIOS_ESCOLARES — enforced by a more specific
 * matcher in {@code SecurityFilterConfig} declared before its blanket
 * {@code /users/**} rule. The caller id used for the mustChangePassword
 * guard and authorization is extracted from the JWT principal, not from the
 * request body.
 */
@RestController
@RequestMapping("/users")
public class UserController {

	private final CreateUserUseCase createUserUseCase;
	private final AssignRoleUseCase assignRoleUseCase;
	private final ListUsersUseCase listUsersUseCase;
	private final GetUserUseCase getUserUseCase;
	private final RevokeRoleUseCase revokeRoleUseCase;
	private final UnlockUserUseCase unlockUserUseCase;

	public UserController(CreateUserUseCase createUserUseCase, AssignRoleUseCase assignRoleUseCase,
			ListUsersUseCase listUsersUseCase, GetUserUseCase getUserUseCase, RevokeRoleUseCase revokeRoleUseCase,
			UnlockUserUseCase unlockUserUseCase) {
		this.createUserUseCase = createUserUseCase;
		this.assignRoleUseCase = assignRoleUseCase;
		this.listUsersUseCase = listUsersUseCase;
		this.getUserUseCase = getUserUseCase;
		this.revokeRoleUseCase = revokeRoleUseCase;
		this.unlockUserUseCase = unlockUserUseCase;
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
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) UUID divisionId) {
		UUID callerId = AuthenticatedCaller.currentUserId();
		ListUsersResult result = listUsersUseCase
				.listUsers(new ListUsersQuery(callerId, role, status, search, page, size, divisionId));
		return ResponseEntity.ok(new UserListResponse(result.users().stream().map(UserController::toItem).toList(),
				result.totalElements(), result.totalPages(), result.page(), result.size()));
	}

	@GetMapping("/{id}")
	public ResponseEntity<UserDetailResponse> getUser(@PathVariable UUID id) {
		UUID callerId = AuthenticatedCaller.currentUserId();
		UserDetailResult result = getUserUseCase.getUser(new GetUserQuery(callerId, id));
		return ResponseEntity.ok(toDetailResponse(result));
	}

	@DeleteMapping("/{userId}/roles/{userRoleId}")
	public ResponseEntity<Void> revokeRole(@PathVariable UUID userId, @PathVariable UUID userRoleId) {
		UUID callerId = AuthenticatedCaller.currentUserId();
		revokeRoleUseCase.revokeRole(new RevokeRoleCommand(callerId, userId, userRoleId));
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/{id}/unlock")
	public ResponseEntity<UnlockUserResponse> unlockUser(@PathVariable UUID id) {
		UUID callerId = AuthenticatedCaller.currentUserId();
		UnlockUserResult result = unlockUserUseCase.unlockUser(new UnlockUserCommand(callerId, id));
		return ResponseEntity.ok(new UnlockUserResponse(result.userId(), result.status(), result.failedLoginAttempts()));
	}

	private static UserListItemResponse toItem(UserSummary summary) {
		return new UserListItemResponse(summary.userId(), summary.personId(), summary.fullName(), summary.username(),
				summary.roles().stream().map(role -> new UserRoleItem(role.roleType(), role.divisionId())).toList(),
				summary.status(), summary.lastLoginAt());
	}

	private static UserDetailResponse toDetailResponse(UserDetailResult result) {
		return new UserDetailResponse(result.userId(), result.personId(), result.fullName(), result.username(),
				result.status(), result.mustChangePassword(), result.lastLoginAt(), result.createdAt(),
				result.roles().stream().map(role -> new UserRoleDetailItem(role.userRoleId(), role.roleType(),
						role.divisionId())).toList());
	}
}
