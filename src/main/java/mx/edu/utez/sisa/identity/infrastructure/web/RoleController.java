package mx.edu.utez.sisa.identity.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.identity.domain.port.in.AssignPermissionsToRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.AssignPermissionsToRoleUseCase.AssignPermissionsCommand;
import mx.edu.utez.sisa.identity.domain.port.in.AssignPermissionsToRoleUseCase.RolePermissionsResult;
import mx.edu.utez.sisa.identity.domain.port.in.ChangeRoleStatusUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.ChangeRoleStatusUseCase.ChangeRoleStatusCommand;
import mx.edu.utez.sisa.identity.domain.port.in.ChangeRoleStatusUseCase.RoleResult;
import mx.edu.utez.sisa.identity.domain.port.in.CreateRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.CreateRoleUseCase.CreateRoleCommand;
import mx.edu.utez.sisa.identity.domain.port.in.GetRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.GetRoleUseCase.RoleDetailResult;
import mx.edu.utez.sisa.identity.domain.port.in.ListRolesUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.ListRolesUseCase.ListRolesQuery;
import mx.edu.utez.sisa.identity.domain.port.in.ListRolesUseCase.ListRolesResult;
import mx.edu.utez.sisa.identity.domain.port.in.ListRolesUseCase.RoleSummary;
import mx.edu.utez.sisa.identity.domain.port.in.UpdateRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.UpdateRoleUseCase.UpdateRoleCommand;
import mx.edu.utez.sisa.identity.domain.model.RoleStatus;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.AssignRolePermissionsRequest;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.ChangeRoleStatusRequest;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.CreateRoleRequest;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.RoleListItemResponse;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.RoleListResponse;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.RoleResponse;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.UpdateRoleRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/roles")
public class RoleController {

	private final CreateRoleUseCase createRoleUseCase;
	private final UpdateRoleUseCase updateRoleUseCase;
	private final ChangeRoleStatusUseCase changeRoleStatusUseCase;
	private final ListRolesUseCase listRolesUseCase;
	private final GetRoleUseCase getRoleUseCase;
	private final AssignPermissionsToRoleUseCase assignPermissionsToRoleUseCase;

	public RoleController(CreateRoleUseCase createRoleUseCase, UpdateRoleUseCase updateRoleUseCase,
			ChangeRoleStatusUseCase changeRoleStatusUseCase, ListRolesUseCase listRolesUseCase,
			GetRoleUseCase getRoleUseCase, AssignPermissionsToRoleUseCase assignPermissionsToRoleUseCase) {
		this.createRoleUseCase = createRoleUseCase;
		this.updateRoleUseCase = updateRoleUseCase;
		this.changeRoleStatusUseCase = changeRoleStatusUseCase;
		this.listRolesUseCase = listRolesUseCase;
		this.getRoleUseCase = getRoleUseCase;
		this.assignPermissionsToRoleUseCase = assignPermissionsToRoleUseCase;
	}

	@PostMapping
	public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
		var result = createRoleUseCase.createRole(new CreateRoleCommand(request.name(), request.key(), request.description()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
	}

	@PutMapping("/{id}")
	public ResponseEntity<RoleResponse> updateRole(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
		var result = updateRoleUseCase.updateRole(new UpdateRoleCommand(id, request.name(), request.key(), request.description()));
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping("/{id}")
	public ResponseEntity<RoleResponse> getRole(@PathVariable UUID id) {
		return ResponseEntity.ok(toResponse(getRoleUseCase.getById(id)));
	}

	@GetMapping
	public ResponseEntity<RoleListResponse> listRoles(@RequestParam(required = false) RoleStatus status,
			@RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		ListRolesResult result = listRolesUseCase.listRoles(new ListRolesQuery(status, search, page, size));
		return ResponseEntity.ok(new RoleListResponse(result.items().stream().map(RoleController::toItem).toList(),
				result.totalElements(), result.totalPages(), result.page(), result.size()));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<RoleResponse> changeStatus(@PathVariable UUID id,
			@Valid @RequestBody ChangeRoleStatusRequest request) {
		RoleResult result = changeRoleStatusUseCase
				.changeStatus(new ChangeRoleStatusCommand(AuthenticatedCaller.currentUserId(), id, request.status()));
		return ResponseEntity.ok(toResponse(result));
	}

	@PutMapping("/{id}/permissions")
	public ResponseEntity<RoleResponse> assignPermissions(@PathVariable UUID id,
			@Valid @RequestBody AssignRolePermissionsRequest request) {
		RolePermissionsResult result = assignPermissionsToRoleUseCase.assignPermissions(
				new AssignPermissionsCommand(AuthenticatedCaller.currentUserId(), id, request.permissionIds()));
		return ResponseEntity.ok(toResponse(result));
	}

	private static RoleResponse toResponse(mx.edu.utez.sisa.identity.domain.port.in.CreateRoleUseCase.RoleResult result) {
		return new RoleResponse(result.id(), result.name(), result.key(), result.status(), result.description(),
				List.of());
	}

	private static RoleResponse toResponse(mx.edu.utez.sisa.identity.domain.port.in.UpdateRoleUseCase.RoleResult result) {
		return new RoleResponse(result.id(), result.name(), result.key(), result.status(), result.description(),
				List.of());
	}

	private static RoleResponse toResponse(RoleResult result) {
		return new RoleResponse(result.id(), result.name(), result.key(), result.status(), result.description(),
				List.of());
	}

	private static RoleResponse toResponse(RolePermissionsResult result) {
		return new RoleResponse(result.id(), result.name(), result.key(), result.status(),
				result.description(), result.permissions().stream().map(permission -> new RoleResponse.RolePermissionItemResponse(
						permission.id(), permission.name(), permission.key(), permission.status())).toList());
	}

	private static RoleResponse toResponse(RoleDetailResult result) {
		return new RoleResponse(result.id(), result.name(), result.key(), result.status(), result.description(),
				result.permissions().stream().map(permission -> new RoleResponse.RolePermissionItemResponse(
						permission.id(), permission.name(), permission.key(), permission.status())).toList());
	}

	private static RoleListItemResponse toItem(RoleSummary summary) {
		return new RoleListItemResponse(summary.id(), summary.name(), summary.key(), summary.status(),
				summary.description());
	}
}