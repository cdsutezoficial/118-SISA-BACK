package mx.edu.utez.sisa.academic_config.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeProgramAdmissionConfigStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeProgramAdmissionConfigStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetProgramAdmissionConfigUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListProgramAdmissionConfigsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListProgramAdmissionConfigsUseCase.ListProgramAdmissionConfigsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListProgramAdmissionConfigsUseCase.ListProgramAdmissionConfigsResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListProgramAdmissionConfigsUseCase.ProgramAdmissionConfigSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.OpenProgramAdmissionUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.OpenProgramAdmissionUseCase.OpenProgramAdmissionCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.OpenProgramAdmissionUseCase.ProgramAdmissionConfigResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateProgramAdmissionConfigUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateProgramAdmissionConfigUseCase.UpdateProgramAdmissionConfigCommand;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.ChangeProgramAdmissionConfigStatusRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.CreateProgramAdmissionConfigRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.ProgramAdmissionConfigListItemResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.ProgramAdmissionConfigListResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.ProgramAdmissionConfigResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.UpdateProgramAdmissionConfigRequest;
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
 * Thin controller for {@code ProgramAdmissionConfig} — a full standalone
 * aggregate (plan: {@code docs/plans/2026-07-28-program-admission-config.md}),
 * same shape as {@code GenerationController}: {@code GET
 * /program-admission-configs} (paginated, filterable by
 * {@code status}/{@code programId}), {@code POST /program-admission-configs}
 * (201), {@code GET /program-admission-configs/{id}} (404 if missing),
 * {@code PUT /program-admission-configs/{id}} (404 if missing, 400 on a bad
 * {@code programId}/{@code periodId}/{@code targetGenerationId} FK, 409 on a
 * {@code (programId, periodId)} conflict), and {@code PATCH
 * /program-admission-configs/{id}/status} (404 if missing — a simple
 * OPEN/CLOSED toggle). Role authorization (ADMIN or SERVICIOS_ESCOLARES) is
 * enforced by {@code identity.SecurityFilterConfig}'s
 * {@code /program-admission-configs} matchers, not here.
 */
@RestController
@RequestMapping("/program-admission-configs")
public class ProgramAdmissionConfigController {

	private final ListProgramAdmissionConfigsUseCase listProgramAdmissionConfigsUseCase;

	private final OpenProgramAdmissionUseCase openProgramAdmissionUseCase;

	private final GetProgramAdmissionConfigUseCase getProgramAdmissionConfigUseCase;

	private final UpdateProgramAdmissionConfigUseCase updateProgramAdmissionConfigUseCase;

	private final ChangeProgramAdmissionConfigStatusUseCase changeProgramAdmissionConfigStatusUseCase;

	public ProgramAdmissionConfigController(ListProgramAdmissionConfigsUseCase listProgramAdmissionConfigsUseCase,
			OpenProgramAdmissionUseCase openProgramAdmissionUseCase,
			GetProgramAdmissionConfigUseCase getProgramAdmissionConfigUseCase,
			UpdateProgramAdmissionConfigUseCase updateProgramAdmissionConfigUseCase,
			ChangeProgramAdmissionConfigStatusUseCase changeProgramAdmissionConfigStatusUseCase) {
		this.listProgramAdmissionConfigsUseCase = listProgramAdmissionConfigsUseCase;
		this.openProgramAdmissionUseCase = openProgramAdmissionUseCase;
		this.getProgramAdmissionConfigUseCase = getProgramAdmissionConfigUseCase;
		this.updateProgramAdmissionConfigUseCase = updateProgramAdmissionConfigUseCase;
		this.changeProgramAdmissionConfigStatusUseCase = changeProgramAdmissionConfigStatusUseCase;
	}

	@PostMapping
	public ResponseEntity<ProgramAdmissionConfigResponse> createProgramAdmissionConfig(
			@Valid @RequestBody CreateProgramAdmissionConfigRequest request) {
		ProgramAdmissionConfigResult result = openProgramAdmissionUseCase
				.openProgramAdmission(new OpenProgramAdmissionCommand(request.programId(), request.periodId(),
						request.targetGenerationId(), request.isOffered(), request.maxCandidates(), request.opensAt(),
						request.closesAt()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ProgramAdmissionConfigResponse> updateProgramAdmissionConfig(@PathVariable UUID id,
			@Valid @RequestBody UpdateProgramAdmissionConfigRequest request) {
		ProgramAdmissionConfigResult result = updateProgramAdmissionConfigUseCase
				.updateProgramAdmissionConfig(new UpdateProgramAdmissionConfigCommand(id, request.programId(),
						request.periodId(), request.targetGenerationId(), request.isOffered(), request.maxCandidates(),
						request.opensAt(), request.closesAt()));
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ProgramAdmissionConfigResponse> getProgramAdmissionConfig(@PathVariable UUID id) {
		ProgramAdmissionConfigResult result = getProgramAdmissionConfigUseCase.getById(id);
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping
	public ResponseEntity<ProgramAdmissionConfigListResponse> listProgramAdmissionConfigs(
			@RequestParam(required = false) ProgramAdmissionConfigStatus status,
			@RequestParam(required = false) UUID programId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		ListProgramAdmissionConfigsResult result = listProgramAdmissionConfigsUseCase
				.listProgramAdmissionConfigs(new ListProgramAdmissionConfigsQuery(status, programId, page, size));
		return ResponseEntity.ok(new ProgramAdmissionConfigListResponse(
				result.items().stream().map(ProgramAdmissionConfigController::toItem).toList(), result.totalElements(),
				result.totalPages(), result.page(), result.size()));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<ProgramAdmissionConfigResponse> changeStatus(@PathVariable UUID id,
			@Valid @RequestBody ChangeProgramAdmissionConfigStatusRequest request) {
		ProgramAdmissionConfigResult result = changeProgramAdmissionConfigStatusUseCase
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

	private static ProgramAdmissionConfigResponse toResponse(ProgramAdmissionConfigResult result) {
		return new ProgramAdmissionConfigResponse(result.id(), result.programId(), result.periodId(),
				result.targetGenerationId(), result.isOffered(), result.maxCandidates(), result.opensAt(),
				result.closesAt(), result.status(), result.selectionStatus());
	}

	private static ProgramAdmissionConfigListItemResponse toItem(ProgramAdmissionConfigSummary summary) {
		return new ProgramAdmissionConfigListItemResponse(summary.id(), summary.programId(), summary.periodId(),
				summary.targetGenerationId(), summary.isOffered(), summary.maxCandidates(), summary.opensAt(),
				summary.closesAt(), summary.status(), summary.selectionStatus());
	}
}
