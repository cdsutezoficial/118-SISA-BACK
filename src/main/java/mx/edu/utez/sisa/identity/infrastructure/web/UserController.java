package mx.edu.utez.sisa.identity.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.identity.domain.port.in.AssignRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.AssignRoleUseCase.AssignRoleCommand;
import mx.edu.utez.sisa.identity.domain.port.in.AssignRoleUseCase.AssignRoleResult;
import mx.edu.utez.sisa.identity.domain.port.in.CreateUserUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.CreateUserUseCase.CreateUserCommand;
import mx.edu.utez.sisa.identity.domain.port.in.CreateUserUseCase.UserCreationResult;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.AssignRoleRequest;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.AssignRoleResponse;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.CreateUserRequest;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.CreateUserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Thin controller for {@code POST /users} and
 * {@code POST /users/{userId}/roles} (design.md — REST endpoints, task 5.6).
 * Both endpoints are ADMIN-only (enforced by {@code SecurityFilterConfig});
 * the caller id used for the mustChangePassword guard and authorization is
 * extracted from the JWT principal, not from the request body.
 */
@RestController
@RequestMapping("/users")
public class UserController {

	private final CreateUserUseCase createUserUseCase;
	private final AssignRoleUseCase assignRoleUseCase;

	public UserController(CreateUserUseCase createUserUseCase, AssignRoleUseCase assignRoleUseCase) {
		this.createUserUseCase = createUserUseCase;
		this.assignRoleUseCase = assignRoleUseCase;
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
}
