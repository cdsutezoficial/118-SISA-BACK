package mx.edu.utez.sisa.identity.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.identity.domain.model.PermissionStatus;
import mx.edu.utez.sisa.identity.domain.port.in.ChangePermissionStatusUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.ChangePermissionStatusUseCase.ChangePermissionStatusCommand;
import mx.edu.utez.sisa.identity.domain.port.in.CreatePermissionUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.CreatePermissionUseCase.CreatePermissionCommand;
import mx.edu.utez.sisa.identity.domain.port.in.GetPermissionUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.ListPermissionsUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.ListPermissionsUseCase.ListPermissionsQuery;
import mx.edu.utez.sisa.identity.domain.port.in.ListPermissionsUseCase.ListPermissionsResult;
import mx.edu.utez.sisa.identity.domain.port.in.ListPermissionsUseCase.PermissionSummary;
import mx.edu.utez.sisa.identity.domain.port.in.UpdatePermissionUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.UpdatePermissionUseCase.UpdatePermissionCommand;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.ChangePermissionStatusRequest;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.CreatePermissionRequest;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.PermissionListItemResponse;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.PermissionListResponse;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.PermissionResponse;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.UpdatePermissionRequest;
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

import java.util.UUID;

@RestController
@RequestMapping("/permissions")
public class PermissionController {

	private final CreatePermissionUseCase createPermissionUseCase;
	private final UpdatePermissionUseCase updatePermissionUseCase;
	private final ChangePermissionStatusUseCase changePermissionStatusUseCase;
	private final ListPermissionsUseCase listPermissionsUseCase;
	private final GetPermissionUseCase getPermissionUseCase;

	public PermissionController(CreatePermissionUseCase createPermissionUseCase,
			UpdatePermissionUseCase updatePermissionUseCase,
			ChangePermissionStatusUseCase changePermissionStatusUseCase,
			ListPermissionsUseCase listPermissionsUseCase, GetPermissionUseCase getPermissionUseCase) {
		this.createPermissionUseCase = createPermissionUseCase;
		this.updatePermissionUseCase = updatePermissionUseCase;
		this.changePermissionStatusUseCase = changePermissionStatusUseCase;
		this.listPermissionsUseCase = listPermissionsUseCase;
		this.getPermissionUseCase = getPermissionUseCase;
	}

	@PostMapping
	public ResponseEntity<PermissionResponse> createPermission(@Valid @RequestBody CreatePermissionRequest request) {
		var result = createPermissionUseCase.createPermission(new CreatePermissionCommand(request.name(), request.key()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
	}

	@PutMapping("/{id}")
	public ResponseEntity<PermissionResponse> updatePermission(@PathVariable UUID id,
			@Valid @RequestBody UpdatePermissionRequest request) {
		var result = updatePermissionUseCase.updatePermission(new UpdatePermissionCommand(id, request.name(), request.key()));
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping("/{id}")
	public ResponseEntity<PermissionResponse> getPermission(@PathVariable UUID id) {
		return ResponseEntity.ok(toResponse(getPermissionUseCase.getById(id)));
	}

	@GetMapping
	public ResponseEntity<PermissionListResponse> listPermissions(@RequestParam(required = false) PermissionStatus status,
			@RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		ListPermissionsResult result = listPermissionsUseCase.listPermissions(
				new ListPermissionsQuery(status, search, page, size));
		return ResponseEntity.ok(new PermissionListResponse(
				result.items().stream().map(PermissionController::toItem).toList()));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<PermissionResponse> changeStatus(@PathVariable UUID id,
			@Valid @RequestBody ChangePermissionStatusRequest request) {
		var result = changePermissionStatusUseCase.changeStatus(
				new ChangePermissionStatusCommand(AuthenticatedCaller.currentUserId(), id, request.status()));
		return ResponseEntity.ok(toResponse(result));
	}

	private static PermissionResponse toResponse(
			mx.edu.utez.sisa.identity.domain.port.in.CreatePermissionUseCase.PermissionResult result) {
		return new PermissionResponse(result.id(), result.name(), result.key(), result.status());
	}

	private static PermissionResponse toResponse(
			mx.edu.utez.sisa.identity.domain.port.in.UpdatePermissionUseCase.PermissionResult result) {
		return new PermissionResponse(result.id(), result.name(), result.key(), result.status());
	}

	private static PermissionResponse toResponse(
			mx.edu.utez.sisa.identity.domain.port.in.ChangePermissionStatusUseCase.PermissionResult result) {
		return new PermissionResponse(result.id(), result.name(), result.key(), result.status());
	}

	private static PermissionResponse toResponse(GetPermissionUseCase.PermissionResult result) {
		return new PermissionResponse(result.id(), result.name(), result.key(), result.status());
	}

	private static PermissionListItemResponse toItem(PermissionSummary summary) {
		return new PermissionListItemResponse(summary.id(), summary.name(), summary.key(), summary.status());
	}
}