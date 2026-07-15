package mx.edu.utez.sisa.academic_config.infrastructure.web;

import mx.edu.utez.sisa.academic_config.domain.model.ClassificationStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListSubjectClassificationsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListSubjectClassificationsUseCase.ClassificationSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListSubjectClassificationsUseCase.ListSubjectClassificationsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListSubjectClassificationsUseCase.ListSubjectClassificationsResult;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.SubjectClassificationListItemResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.SubjectClassificationListResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin controller for {@code SubjectClassification} — Phase 1 (List) only:
 * {@code GET /subject-classifications} (paginated). Create/Update/Get/
 * ChangeStatus are future phases (see
 * {@code docs/plans/2026-07-15-subject-classification-crud.md}). Role
 * authorization (ADMIN or SERVICIOS_ESCOLARES) is enforced by
 * {@code identity.SecurityFilterConfig}'s {@code /subject-classifications}
 * matcher, not here.
 */
@RestController
@RequestMapping("/subject-classifications")
public class SubjectClassificationController {

	private final ListSubjectClassificationsUseCase listSubjectClassificationsUseCase;

	public SubjectClassificationController(ListSubjectClassificationsUseCase listSubjectClassificationsUseCase) {
		this.listSubjectClassificationsUseCase = listSubjectClassificationsUseCase;
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

	private static SubjectClassificationListItemResponse toItem(ClassificationSummary summary) {
		return new SubjectClassificationListItemResponse(summary.id(), summary.name(), summary.code(),
				summary.status());
	}
}
