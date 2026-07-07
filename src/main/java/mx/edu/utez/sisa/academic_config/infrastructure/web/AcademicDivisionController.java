package mx.edu.utez.sisa.academic_config.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.academic_config.domain.model.DivisionStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicDivisionStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicDivisionStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase.AcademicDivisionResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicDivisionUseCase.CreateAcademicDivisionCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicDivisionsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicDivisionsUseCase.DivisionSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicDivisionsUseCase.ListAcademicDivisionsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicDivisionsUseCase.ListAcademicDivisionsResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicDivisionUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicDivisionUseCase.UpdateAcademicDivisionCommand;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.AcademicDivisionListItemResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.AcademicDivisionListResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.AcademicDivisionResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.ChangeDivisionStatusRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.CreateAcademicDivisionRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.UpdateAcademicDivisionRequest;
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
 * Thin controller for {@code AcademicDivision} CRUD (spec: "Academic
 * Division Management"): {@code POST /divisions} (201),
 * {@code PUT /divisions/{id}}, {@code GET /divisions} (paginated),
 * {@code PATCH /divisions/{id}/status}. Role authorization (ADMIN or
 * SERVICIOS_ESCOLARES on every verb) is enforced by
 * {@code identity.SecurityFilterConfig}'s split {@code /divisions} matchers,
 * not here.
 */
@RestController
@RequestMapping("/divisions")
public class AcademicDivisionController {

	private final CreateAcademicDivisionUseCase createAcademicDivisionUseCase;

	private final UpdateAcademicDivisionUseCase updateAcademicDivisionUseCase;

	private final ListAcademicDivisionsUseCase listAcademicDivisionsUseCase;

	private final ChangeAcademicDivisionStatusUseCase changeAcademicDivisionStatusUseCase;

	public AcademicDivisionController(CreateAcademicDivisionUseCase createAcademicDivisionUseCase,
			UpdateAcademicDivisionUseCase updateAcademicDivisionUseCase,
			ListAcademicDivisionsUseCase listAcademicDivisionsUseCase,
			ChangeAcademicDivisionStatusUseCase changeAcademicDivisionStatusUseCase) {
		this.createAcademicDivisionUseCase = createAcademicDivisionUseCase;
		this.updateAcademicDivisionUseCase = updateAcademicDivisionUseCase;
		this.listAcademicDivisionsUseCase = listAcademicDivisionsUseCase;
		this.changeAcademicDivisionStatusUseCase = changeAcademicDivisionStatusUseCase;
	}

	@PostMapping
	public ResponseEntity<AcademicDivisionResponse> createDivision(
			@Valid @RequestBody CreateAcademicDivisionRequest request) {
		AcademicDivisionResult result = createAcademicDivisionUseCase.createDivision(new CreateAcademicDivisionCommand(
				request.name(), request.code(), request.description(), request.directorPersonId()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
	}

	@PutMapping("/{id}")
	public ResponseEntity<AcademicDivisionResponse> updateDivision(@PathVariable UUID id,
			@Valid @RequestBody UpdateAcademicDivisionRequest request) {
		AcademicDivisionResult result = updateAcademicDivisionUseCase.updateDivision(new UpdateAcademicDivisionCommand(
				id, request.name(), request.code(), request.description(), request.directorPersonId()));
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping
	public ResponseEntity<AcademicDivisionListResponse> listDivisions(
			@RequestParam(required = false) DivisionStatus status, @RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		ListAcademicDivisionsResult result = listAcademicDivisionsUseCase
				.listDivisions(new ListAcademicDivisionsQuery(status, search, page, size));
		return ResponseEntity.ok(new AcademicDivisionListResponse(
				result.items().stream().map(AcademicDivisionController::toItem).toList(), result.totalElements(),
				result.totalPages(), result.page(), result.size()));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<AcademicDivisionResponse> changeStatus(@PathVariable UUID id,
			@Valid @RequestBody ChangeDivisionStatusRequest request) {
		AcademicDivisionResult result = changeAcademicDivisionStatusUseCase
				.changeStatus(new ChangeStatusCommand(currentUserId(), id, request.status()));
		return ResponseEntity.ok(toResponse(result));
	}

	/**
	 * Extracts the acting user's id from the JWT principal
	 * ({@link mx.edu.utez.sisa.identity.infrastructure.security.JwtAuthenticationFilter}
	 * sets it to the token's {@code sub} claim) — same mechanism as
	 * {@code identity.AuthenticatedCaller}, reimplemented here since that
	 * class is package-private to {@code identity.infrastructure.web}. Only
	 * consumed by {@link ChangeStatusCommand#callerId()}, which is reserved
	 * for future audit-log attribution and not read by the use case yet.
	 */
	private static UUID currentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return UUID.fromString(authentication.getName());
	}

	private static AcademicDivisionResponse toResponse(AcademicDivisionResult result) {
		return new AcademicDivisionResponse(result.id(), result.name(), result.code(), result.description(),
				result.directorPersonId(), result.status());
	}

	private static AcademicDivisionListItemResponse toItem(DivisionSummary summary) {
		return new AcademicDivisionListItemResponse(summary.id(), summary.name(), summary.code(),
				summary.description(), summary.directorPersonId(), summary.status(), summary.programCount());
	}
}
