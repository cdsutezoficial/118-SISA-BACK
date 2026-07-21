package mx.edu.utez.sisa.academic_config.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.academic_config.domain.model.GenerationStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeGenerationStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeGenerationStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGenerationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGenerationUseCase.CreateGenerationCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGenerationUseCase.GenerationResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetGenerationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGenerationsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGenerationsUseCase.GenerationSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGenerationsUseCase.ListGenerationsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGenerationsUseCase.ListGenerationsResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateGenerationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateGenerationUseCase.UpdateGenerationCommand;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.ChangeGenerationStatusRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.CreateGenerationRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.GenerationListItemResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.GenerationListResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.GenerationResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.UpdateGenerationRequest;
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
 * Thin controller for {@code Generation} — a full standalone aggregate (plan:
 * {@code docs/plans/2026-07-20-generation-group.md}), same shape as
 * {@code AcademicPeriodController}: {@code GET /generations} (paginated,
 * filterable by {@code status}/{@code search}/{@code programId}),
 * {@code POST /generations} (201), {@code GET /generations/{id}} (404 if
 * missing), {@code PUT /generations/{id}} (404 if missing, 400 on a bad
 * {@code planId}/{@code startPeriodId} FK, 409 on a {@code number} conflict
 * within the same program), and {@code PATCH /generations/{id}/status} (404
 * if missing — a simple ACTIVE/FINISHED toggle, unlike
 * {@code AcademicPeriodController}'s sequential lifecycle). Role
 * authorization (ADMIN or SERVICIOS_ESCOLARES) is enforced by
 * {@code identity.SecurityFilterConfig}'s {@code /generations} matchers, not
 * here.
 */
@RestController
@RequestMapping("/generations")
public class GenerationController {

	private final ListGenerationsUseCase listGenerationsUseCase;

	private final CreateGenerationUseCase createGenerationUseCase;

	private final GetGenerationUseCase getGenerationUseCase;

	private final UpdateGenerationUseCase updateGenerationUseCase;

	private final ChangeGenerationStatusUseCase changeGenerationStatusUseCase;

	public GenerationController(ListGenerationsUseCase listGenerationsUseCase,
			CreateGenerationUseCase createGenerationUseCase, GetGenerationUseCase getGenerationUseCase,
			UpdateGenerationUseCase updateGenerationUseCase,
			ChangeGenerationStatusUseCase changeGenerationStatusUseCase) {
		this.listGenerationsUseCase = listGenerationsUseCase;
		this.createGenerationUseCase = createGenerationUseCase;
		this.getGenerationUseCase = getGenerationUseCase;
		this.updateGenerationUseCase = updateGenerationUseCase;
		this.changeGenerationStatusUseCase = changeGenerationStatusUseCase;
	}

	@PostMapping
	public ResponseEntity<GenerationResponse> createGeneration(@Valid @RequestBody CreateGenerationRequest request) {
		GenerationResult result = createGenerationUseCase
				.createGeneration(new CreateGenerationCommand(request.planId(), request.startPeriodId(), request.number()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
	}

	@PutMapping("/{id}")
	public ResponseEntity<GenerationResponse> updateGeneration(@PathVariable UUID id,
			@Valid @RequestBody UpdateGenerationRequest request) {
		GenerationResult result = updateGenerationUseCase.updateGeneration(
				new UpdateGenerationCommand(id, request.planId(), request.startPeriodId(), request.number()));
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping("/{id}")
	public ResponseEntity<GenerationResponse> getGeneration(@PathVariable UUID id) {
		GenerationResult result = getGenerationUseCase.getById(id);
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping
	public ResponseEntity<GenerationListResponse> listGenerations(
			@RequestParam(required = false) GenerationStatus status, @RequestParam(required = false) String search,
			@RequestParam(required = false) UUID programId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		ListGenerationsResult result = listGenerationsUseCase
				.listGenerations(new ListGenerationsQuery(status, search, programId, page, size));
		return ResponseEntity.ok(new GenerationListResponse(
				result.items().stream().map(GenerationController::toItem).toList(), result.totalElements(),
				result.totalPages(), result.page(), result.size()));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<GenerationResponse> changeStatus(@PathVariable UUID id,
			@Valid @RequestBody ChangeGenerationStatusRequest request) {
		GenerationResult result = changeGenerationStatusUseCase
				.changeStatus(new ChangeStatusCommand(currentUserId(), id, request.status()));
		return ResponseEntity.ok(toResponse(result));
	}

	/**
	 * Extracts the acting user's id from the JWT principal — same mechanism as
	 * {@code AcademicPeriodController#currentUserId}. Only consumed by
	 * {@link ChangeStatusCommand#callerId()}, reserved for future audit-log
	 * attribution and not read by the use case yet.
	 */
	private static UUID currentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return UUID.fromString(authentication.getName());
	}

	private static GenerationResponse toResponse(GenerationResult result) {
		return new GenerationResponse(result.id(), result.planId(), result.startPeriodId(), result.programId(),
				result.number(), result.code(), result.status());
	}

	private static GenerationListItemResponse toItem(GenerationSummary summary) {
		return new GenerationListItemResponse(summary.id(), summary.planId(), summary.startPeriodId(),
				summary.programId(), summary.number(), summary.code(), summary.status());
	}
}
