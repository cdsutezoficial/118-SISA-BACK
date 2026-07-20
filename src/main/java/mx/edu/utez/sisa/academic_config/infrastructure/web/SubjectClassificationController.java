package mx.edu.utez.sisa.academic_config.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.academic_config.domain.model.ClassificationStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeSubjectClassificationStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeSubjectClassificationStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateSubjectClassificationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateSubjectClassificationUseCase.ClassificationResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateSubjectClassificationUseCase.CreateClassificationCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetSubjectClassificationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListSubjectClassificationsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListSubjectClassificationsUseCase.ClassificationSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListSubjectClassificationsUseCase.ListSubjectClassificationsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListSubjectClassificationsUseCase.ListSubjectClassificationsResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateSubjectClassificationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateSubjectClassificationUseCase.UpdateClassificationCommand;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.ChangeClassificationStatusRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.CreateSubjectClassificationRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.SubjectClassificationListItemResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.SubjectClassificationListResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.SubjectClassificationResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.UpdateSubjectClassificationRequest;
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
 * Thin controller for {@code SubjectClassification} — Phase 1 (List), Phase 2
 * (Create), Phase 3 (Get by id), Phase 4 (Update), Phase 5 (ChangeStatus):
 * {@code GET /subject-classifications} (paginated),
 * {@code POST /subject-classifications} (201),
 * {@code GET /subject-classifications/{id}} (404 if missing),
 * {@code PUT /subject-classifications/{id}} (404 if missing, 409 on code
 * conflict with a different record), and
 * {@code PATCH /subject-classifications/{id}/status} (404 if missing) — the
 * aggregate's full CRUD is now complete (see
 * {@code docs/plans/2026-07-15-subject-classification-crud.md}). Role
 * authorization (ADMIN or SERVICIOS_ESCOLARES) is enforced by
 * {@code identity.SecurityFilterConfig}'s {@code /subject-classifications}
 * matchers, not here.
 */
@RestController
@RequestMapping("/subject-classifications")
public class SubjectClassificationController {

	private final ListSubjectClassificationsUseCase listSubjectClassificationsUseCase;

	private final CreateSubjectClassificationUseCase createSubjectClassificationUseCase;

	private final GetSubjectClassificationUseCase getSubjectClassificationUseCase;

	private final UpdateSubjectClassificationUseCase updateSubjectClassificationUseCase;

	private final ChangeSubjectClassificationStatusUseCase changeSubjectClassificationStatusUseCase;

	public SubjectClassificationController(ListSubjectClassificationsUseCase listSubjectClassificationsUseCase,
			CreateSubjectClassificationUseCase createSubjectClassificationUseCase,
			GetSubjectClassificationUseCase getSubjectClassificationUseCase,
			UpdateSubjectClassificationUseCase updateSubjectClassificationUseCase,
			ChangeSubjectClassificationStatusUseCase changeSubjectClassificationStatusUseCase) {
		this.listSubjectClassificationsUseCase = listSubjectClassificationsUseCase;
		this.createSubjectClassificationUseCase = createSubjectClassificationUseCase;
		this.getSubjectClassificationUseCase = getSubjectClassificationUseCase;
		this.updateSubjectClassificationUseCase = updateSubjectClassificationUseCase;
		this.changeSubjectClassificationStatusUseCase = changeSubjectClassificationStatusUseCase;
	}

	@PostMapping
	public ResponseEntity<SubjectClassificationResponse> createClassification(
			@Valid @RequestBody CreateSubjectClassificationRequest request) {
		ClassificationResult result = createSubjectClassificationUseCase
				.createClassification(new CreateClassificationCommand(request.name(), request.code()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
	}

	@PutMapping("/{id}")
	public ResponseEntity<SubjectClassificationResponse> updateClassification(@PathVariable UUID id,
			@Valid @RequestBody UpdateSubjectClassificationRequest request) {
		ClassificationResult result = updateSubjectClassificationUseCase
				.updateClassification(new UpdateClassificationCommand(id, request.name(), request.code()));
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping("/{id}")
	public ResponseEntity<SubjectClassificationResponse> getClassification(@PathVariable UUID id) {
		ClassificationResult result = getSubjectClassificationUseCase.getById(id);
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping
	public ResponseEntity<SubjectClassificationListResponse> listClassifications(
			@RequestParam(required = false) ClassificationStatus status,
			@RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		ListSubjectClassificationsResult result = listSubjectClassificationsUseCase
				.listClassifications(new ListSubjectClassificationsQuery(status, search, page, size));
		return ResponseEntity.ok(new SubjectClassificationListResponse(
				result.items().stream().map(SubjectClassificationController::toItem).toList(), result.totalElements(),
				result.totalPages(), result.page(), result.size()));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<SubjectClassificationResponse> changeStatus(@PathVariable UUID id,
			@Valid @RequestBody ChangeClassificationStatusRequest request) {
		ClassificationResult result = changeSubjectClassificationStatusUseCase
				.changeStatus(new ChangeStatusCommand(currentUserId(), id, request.status()));
		return ResponseEntity.ok(toResponse(result));
	}

	/**
	 * Extracts the acting user's id from the JWT principal
	 * ({@link mx.edu.utez.sisa.identity.infrastructure.security.JwtAuthenticationFilter}
	 * sets it to the token's {@code sub} claim) — same mechanism as
	 * {@code AcademicDivisionController#currentUserId}. Only consumed by
	 * {@link ChangeStatusCommand#callerId()}, which is reserved for future
	 * audit-log attribution and not read by the use case yet.
	 */
	private static UUID currentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return UUID.fromString(authentication.getName());
	}

	private static SubjectClassificationResponse toResponse(ClassificationResult result) {
		return new SubjectClassificationResponse(result.id(), result.name(), result.code(), result.status());
	}

	private static SubjectClassificationListItemResponse toItem(ClassificationSummary summary) {
		return new SubjectClassificationListItemResponse(summary.id(), summary.name(), summary.code(),
				summary.status());
	}
}
