package mx.edu.utez.sisa.academic_config.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.academic_config.domain.model.ClassificationStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateSubjectClassificationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateSubjectClassificationUseCase.ClassificationResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateSubjectClassificationUseCase.CreateClassificationCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetSubjectClassificationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListSubjectClassificationsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListSubjectClassificationsUseCase.ClassificationSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListSubjectClassificationsUseCase.ListSubjectClassificationsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListSubjectClassificationsUseCase.ListSubjectClassificationsResult;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.CreateSubjectClassificationRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.SubjectClassificationListItemResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.SubjectClassificationListResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.SubjectClassificationResponse;
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
 * Thin controller for {@code SubjectClassification} — Phase 1 (List), Phase 2
 * (Create), Phase 3 (Get by id): {@code GET /subject-classifications}
 * (paginated), {@code POST /subject-classifications} (201), and
 * {@code GET /subject-classifications/{id}} (404 if missing). Update/
 * ChangeStatus are future phases (see
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

	public SubjectClassificationController(ListSubjectClassificationsUseCase listSubjectClassificationsUseCase,
			CreateSubjectClassificationUseCase createSubjectClassificationUseCase,
			GetSubjectClassificationUseCase getSubjectClassificationUseCase) {
		this.listSubjectClassificationsUseCase = listSubjectClassificationsUseCase;
		this.createSubjectClassificationUseCase = createSubjectClassificationUseCase;
		this.getSubjectClassificationUseCase = getSubjectClassificationUseCase;
	}

	@PostMapping
	public ResponseEntity<SubjectClassificationResponse> createClassification(
			@Valid @RequestBody CreateSubjectClassificationRequest request) {
		ClassificationResult result = createSubjectClassificationUseCase
				.createClassification(new CreateClassificationCommand(request.name(), request.code()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
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

	private static SubjectClassificationResponse toResponse(ClassificationResult result) {
		return new SubjectClassificationResponse(result.id(), result.name(), result.code(), result.status());
	}

	private static SubjectClassificationListItemResponse toItem(ClassificationSummary summary) {
		return new SubjectClassificationListItemResponse(summary.id(), summary.name(), summary.code(),
				summary.status());
	}
}
