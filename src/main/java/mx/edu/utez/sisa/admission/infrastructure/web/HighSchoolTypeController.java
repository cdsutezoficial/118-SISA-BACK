package mx.edu.utez.sisa.admission.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.admission.domain.model.HighSchoolTypeStatus;
import mx.edu.utez.sisa.admission.domain.port.in.ChangeHighSchoolTypeStatusUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ChangeHighSchoolTypeStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase.CreateHighSchoolTypeCommand;
import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase.HighSchoolTypeResult;
import mx.edu.utez.sisa.admission.domain.port.in.GetHighSchoolTypeUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ListHighSchoolTypesUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ListHighSchoolTypesUseCase.ListHighSchoolTypesQuery;
import mx.edu.utez.sisa.admission.domain.port.in.ListHighSchoolTypesUseCase.ListHighSchoolTypesResult;
import mx.edu.utez.sisa.admission.domain.port.in.ListHighSchoolTypesUseCase.HighSchoolTypeSummary;
import mx.edu.utez.sisa.admission.domain.port.in.UpdateHighSchoolTypeUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.UpdateHighSchoolTypeUseCase.UpdateHighSchoolTypeCommand;
import mx.edu.utez.sisa.admission.infrastructure.persistence.HighSchoolTypeJpaRepository;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.ChangeHighSchoolTypeStatusRequest;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.CreateHighSchoolTypeRequest;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.HighSchoolTypeListItemResponse;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.HighSchoolTypeListResponse;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.HighSchoolTypeResponse;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.UpdateHighSchoolTypeRequest;
import mx.edu.utez.sisa.shared.web.dto.OptionResponse;
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

import java.util.List;
import java.util.UUID;

/**
 * Thin controller for {@code HighSchoolType} — second aggregate of the
 * {@code admission} bounded context: {@code GET /high-school-types}
 * (paginated), {@code POST /high-school-types} (201),
 * {@code GET /high-school-types/{id}} (404 if missing),
 * {@code PUT /high-school-types/{id}} (404 if missing), and
 * {@code PATCH /high-school-types/{id}/status} (404 if missing). Role
 * authorization (ADMIN or SERVICIOS_ESCOLARES) is enforced by
 * {@code identity.SecurityFilterConfig}'s {@code /high-school-types}
 * matchers, not here. Mirrors {@code OutreachChannelController} exactly.
 */
@RestController
@RequestMapping("/high-school-types")
public class HighSchoolTypeController {

	private final ListHighSchoolTypesUseCase listHighSchoolTypesUseCase;

	private final CreateHighSchoolTypeUseCase createHighSchoolTypeUseCase;

	private final GetHighSchoolTypeUseCase getHighSchoolTypeUseCase;

	private final UpdateHighSchoolTypeUseCase updateHighSchoolTypeUseCase;

	private final ChangeHighSchoolTypeStatusUseCase changeHighSchoolTypeStatusUseCase;

	private final HighSchoolTypeJpaRepository highSchoolTypeJpaRepository;

	public HighSchoolTypeController(ListHighSchoolTypesUseCase listHighSchoolTypesUseCase,
			CreateHighSchoolTypeUseCase createHighSchoolTypeUseCase,
			GetHighSchoolTypeUseCase getHighSchoolTypeUseCase,
			UpdateHighSchoolTypeUseCase updateHighSchoolTypeUseCase,
			ChangeHighSchoolTypeStatusUseCase changeHighSchoolTypeStatusUseCase,
			HighSchoolTypeJpaRepository highSchoolTypeJpaRepository) {
		this.listHighSchoolTypesUseCase = listHighSchoolTypesUseCase;
		this.createHighSchoolTypeUseCase = createHighSchoolTypeUseCase;
		this.getHighSchoolTypeUseCase = getHighSchoolTypeUseCase;
		this.updateHighSchoolTypeUseCase = updateHighSchoolTypeUseCase;
		this.changeHighSchoolTypeStatusUseCase = changeHighSchoolTypeStatusUseCase;
		this.highSchoolTypeJpaRepository = highSchoolTypeJpaRepository;
	}

	@PostMapping
	public ResponseEntity<HighSchoolTypeResponse> createHighSchoolType(
			@Valid @RequestBody CreateHighSchoolTypeRequest request) {
		HighSchoolTypeResult result = createHighSchoolTypeUseCase
				.createHighSchoolType(new CreateHighSchoolTypeCommand(request.name()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
	}

	@PutMapping("/{id}")
	public ResponseEntity<HighSchoolTypeResponse> updateHighSchoolType(@PathVariable UUID id,
			@Valid @RequestBody UpdateHighSchoolTypeRequest request) {
		HighSchoolTypeResult result = updateHighSchoolTypeUseCase
				.updateHighSchoolType(new UpdateHighSchoolTypeCommand(id, request.name()));
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping("/options")
	public List<OptionResponse> listHighSchoolTypeOptions() {
		return highSchoolTypeJpaRepository.findByStatusOrderByNameAsc(HighSchoolTypeStatus.ACTIVE).stream()
				.map(h -> new OptionResponse(h.getId(), h.getName(), null)).toList();
	}

	@GetMapping("/{id}")
	public ResponseEntity<HighSchoolTypeResponse> getHighSchoolType(@PathVariable UUID id) {
		HighSchoolTypeResult result = getHighSchoolTypeUseCase.getById(id);
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping
	public ResponseEntity<HighSchoolTypeListResponse> listHighSchoolTypes(
			@RequestParam(required = false) HighSchoolTypeStatus status,
			@RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		ListHighSchoolTypesResult result = listHighSchoolTypesUseCase
				.listHighSchoolTypes(new ListHighSchoolTypesQuery(status, search, page, size));
		return ResponseEntity.ok(new HighSchoolTypeListResponse(
				result.items().stream().map(HighSchoolTypeController::toItem).toList(), result.totalElements(),
				result.totalPages(), result.page(), result.size()));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<HighSchoolTypeResponse> changeStatus(@PathVariable UUID id,
			@Valid @RequestBody ChangeHighSchoolTypeStatusRequest request) {
		HighSchoolTypeResult result = changeHighSchoolTypeStatusUseCase
				.changeStatus(new ChangeStatusCommand(currentUserId(), id, request.status()));
		return ResponseEntity.ok(toResponse(result));
	}

	/**
	 * Extracts the acting user's id from the JWT principal — same mechanism
	 * as {@code OutreachChannelController#currentUserId}. Only consumed by
	 * {@link ChangeStatusCommand#callerId()}, reserved for future audit-log
	 * attribution.
	 */
	private static UUID currentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return UUID.fromString(authentication.getName());
	}

	private static HighSchoolTypeResponse toResponse(HighSchoolTypeResult result) {
		return new HighSchoolTypeResponse(result.id(), result.name(), result.status());
	}

	private static HighSchoolTypeListItemResponse toItem(HighSchoolTypeSummary summary) {
		return new HighSchoolTypeListItemResponse(summary.id(), summary.name(), summary.status());
	}
}
