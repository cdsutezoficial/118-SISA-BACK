package mx.edu.utez.sisa.academic_config.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.academic_config.domain.model.GroupStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeGroupStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeGroupStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupUseCase.CreateGroupCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupUseCase.GroupResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetGroupUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGroupsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGroupsUseCase.GroupSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGroupsUseCase.ListGroupsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGroupsUseCase.ListGroupsResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateGroupUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateGroupUseCase.UpdateGroupCommand;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.ChangeGroupStatusRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.CreateGroupRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.GroupListItemResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.GroupListResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.GroupResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.UpdateGroupRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

/**
 * Thin controller for {@code Group} — a full standalone aggregate (plan:
 * {@code docs/plans/2026-07-20-generation-group.md}), same shape as
 * {@code GenerationController}: {@code GET /groups} (paginated, filterable
 * by {@code status}/{@code search}/{@code programId}/{@code generationId}),
 * {@code POST /groups} (201, 400 on a bad {@code generationId}/
 * {@code periodId}/{@code planLevelId} FK), {@code GET /groups/{id}} (404 if
 * missing), {@code PUT /groups/{id}} (404 if missing, 400 on a bad FK), and
 * {@code PATCH /groups/{id}/status} (404 if missing — a simple OPEN/CLOSED
 * toggle, same shape as {@code GenerationController}'s ACTIVE/FINISHED
 * toggle). Role authorization (ADMIN or SERVICIOS_ESCOLARES) is enforced by
 * {@code identity.SecurityFilterConfig}'s {@code /groups} matchers, not here.
 */
@RestController
@RequestMapping("/groups")
public class GroupController {

	private final ListGroupsUseCase listGroupsUseCase;

	private final CreateGroupUseCase createGroupUseCase;

	private final GetGroupUseCase getGroupUseCase;

	private final UpdateGroupUseCase updateGroupUseCase;

	private final ChangeGroupStatusUseCase changeGroupStatusUseCase;

	public GroupController(ListGroupsUseCase listGroupsUseCase, CreateGroupUseCase createGroupUseCase,
			GetGroupUseCase getGroupUseCase, UpdateGroupUseCase updateGroupUseCase,
			ChangeGroupStatusUseCase changeGroupStatusUseCase) {
		this.listGroupsUseCase = listGroupsUseCase;
		this.createGroupUseCase = createGroupUseCase;
		this.getGroupUseCase = getGroupUseCase;
		this.updateGroupUseCase = updateGroupUseCase;
		this.changeGroupStatusUseCase = changeGroupStatusUseCase;
	}

	@PostMapping
	public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request) {
		GroupResult result = createGroupUseCase.createGroup(new CreateGroupCommand(request.generationId(),
				request.periodId(), request.planLevelId(), request.code(), request.maxCapacity(), request.shift()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
	}

	@PutMapping("/{id}")
	public ResponseEntity<GroupResponse> updateGroup(@PathVariable UUID id,
			@Valid @RequestBody UpdateGroupRequest request) {
		GroupResult result = updateGroupUseCase.updateGroup(new UpdateGroupCommand(id, request.generationId(),
				request.periodId(), request.planLevelId(), request.code(), request.maxCapacity(), request.shift()));
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping("/{id}")
	public ResponseEntity<GroupResponse> getGroup(@PathVariable UUID id) {
		GroupResult result = getGroupUseCase.getById(id);
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping
	public ResponseEntity<GroupListResponse> listGroups(@RequestParam(required = false) GroupStatus status,
			@RequestParam(required = false) String search, @RequestParam(required = false) UUID programId,
			@RequestParam(required = false) UUID generationId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		ListGroupsResult result = listGroupsUseCase
				.listGroups(new ListGroupsQuery(status, search, programId, generationId, page, size));
		return ResponseEntity.ok(new GroupListResponse(result.items().stream().map(GroupController::toItem).toList(),
				result.totalElements(), result.totalPages(), result.page(), result.size()));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<GroupResponse> changeStatus(@PathVariable UUID id,
			@Valid @RequestBody ChangeGroupStatusRequest request) {
		GroupResult result = changeGroupStatusUseCase
				.changeStatus(new ChangeStatusCommand(currentUserId(), id, request.status()));
		return ResponseEntity.ok(toResponse(result));
	}

	/**
	 * Extracts the acting user's id from the JWT principal — same mechanism as
	 * {@code GenerationController#currentUserId}. Only consumed by
	 * {@link ChangeStatusCommand#callerId()}, reserved for future audit-log
	 * attribution and not read by the use case yet.
	 */
	private static UUID currentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return UUID.fromString(authentication.getName());
	}

	private static GroupResponse toResponse(GroupResult result) {
		return new GroupResponse(result.id(), result.generationId(), result.periodId(), result.planLevelId(),
				result.programId(), result.code(), result.maxCapacity(), result.shift(), result.status());
	}

	private static GroupListItemResponse toItem(GroupSummary summary) {
		return new GroupListItemResponse(summary.id(), summary.generationId(), summary.periodId(),
				summary.planLevelId(), summary.programId(), summary.code(), summary.maxCapacity(), summary.shift(),
				summary.status());
	}
}
