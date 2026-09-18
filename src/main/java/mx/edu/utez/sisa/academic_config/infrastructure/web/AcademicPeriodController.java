package mx.edu.utez.sisa.academic_config.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.academic_config.domain.port.in.AdvanceAcademicPeriodStatusByDateUseCase;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicPeriodStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicPeriodStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase.CreatePeriodCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPeriodUseCase.PeriodResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetAcademicPeriodUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPeriodsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPeriodsUseCase.ListAcademicPeriodsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPeriodsUseCase.ListAcademicPeriodsResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPeriodsUseCase.PeriodSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicPeriodUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicPeriodUseCase.UpdatePeriodCommand;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.AcademicPeriodJpaRepository;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.AcademicPeriodListItemResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.AcademicPeriodListResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.AcademicPeriodResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.AdvancePeriodsByDateResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.ChangePeriodStatusRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.CreateAcademicPeriodRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.UpdateAcademicPeriodRequest;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Thin controller for {@code AcademicPeriod} — a full standalone aggregate
 * (plan: {@code docs/plans/2026-07-20-academic-period.md}), same shape as
 * {@code SubjectClassificationController}: {@code GET /periods} (paginated),
 * {@code POST /periods} (201), {@code GET /periods/{id}} (404 if missing),
 * {@code PUT /periods/{id}} (404 if missing, 409 on {@code (year,
 * periodNumber)} conflict with a different record), and
 * {@code PATCH /periods/{id}/status} (404 if missing, 400 on an invalid
 * sequential transition). {@code POST /periods/advance-by-date} runs the same
 * date-threshold walk as the daily job {@code AdvanceAcademicPeriodStatusJob}
 * on demand, so the front gets fresh statuses the moment the list opens.
 * Role authorization (ADMIN or SERVICIOS_ESCOLARES)
 * is enforced by {@code identity.SecurityFilterConfig}'s {@code /periods}
 * matchers, not here.
 */
@RestController
@RequestMapping("/periods")
public class AcademicPeriodController {

	private final ListAcademicPeriodsUseCase listAcademicPeriodsUseCase;

	private final CreateAcademicPeriodUseCase createAcademicPeriodUseCase;

	private final GetAcademicPeriodUseCase getAcademicPeriodUseCase;

	private final UpdateAcademicPeriodUseCase updateAcademicPeriodUseCase;

	private final ChangeAcademicPeriodStatusUseCase changeAcademicPeriodStatusUseCase;

	private final AdvanceAcademicPeriodStatusByDateUseCase advanceAcademicPeriodStatusByDateUseCase;

	private final AcademicPeriodJpaRepository academicPeriodJpaRepository;

	public AcademicPeriodController(ListAcademicPeriodsUseCase listAcademicPeriodsUseCase,
			CreateAcademicPeriodUseCase createAcademicPeriodUseCase, GetAcademicPeriodUseCase getAcademicPeriodUseCase,
			UpdateAcademicPeriodUseCase updateAcademicPeriodUseCase,
			ChangeAcademicPeriodStatusUseCase changeAcademicPeriodStatusUseCase,
			AdvanceAcademicPeriodStatusByDateUseCase advanceAcademicPeriodStatusByDateUseCase,
			AcademicPeriodJpaRepository academicPeriodJpaRepository) {
		this.listAcademicPeriodsUseCase = listAcademicPeriodsUseCase;
		this.createAcademicPeriodUseCase = createAcademicPeriodUseCase;
		this.getAcademicPeriodUseCase = getAcademicPeriodUseCase;
		this.updateAcademicPeriodUseCase = updateAcademicPeriodUseCase;
		this.changeAcademicPeriodStatusUseCase = changeAcademicPeriodStatusUseCase;
		this.advanceAcademicPeriodStatusByDateUseCase = advanceAcademicPeriodStatusByDateUseCase;
		this.academicPeriodJpaRepository = academicPeriodJpaRepository;
	}

	@PostMapping
	public ResponseEntity<AcademicPeriodResponse> createPeriod(@Valid @RequestBody CreateAcademicPeriodRequest request) {
		PeriodResult result = createAcademicPeriodUseCase.createPeriod(new CreatePeriodCommand(request.name(),
				request.year(), request.periodNumber(), request.type(), request.startDate(), request.endDate(),
				request.enrollmentStart(), request.enrollmentEnd()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
	}

	@PutMapping("/{id}")
	public ResponseEntity<AcademicPeriodResponse> updatePeriod(@PathVariable UUID id,
			@Valid @RequestBody UpdateAcademicPeriodRequest request) {
		PeriodResult result = updateAcademicPeriodUseCase.updatePeriod(new UpdatePeriodCommand(id, request.name(),
				request.year(), request.periodNumber(), request.type(), request.startDate(), request.endDate(),
				request.enrollmentStart(), request.enrollmentEnd()));
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping("/options")
	public List<OptionResponse> listPeriodOptions() {
		return academicPeriodJpaRepository.findByStatusOrderByYearDescNameAsc(PeriodStatus.ACTIVE).stream()
				.map(p -> new OptionResponse(p.getId(), p.getName(), String.valueOf(p.getYear()))).toList();
	}

	@GetMapping("/{id}")
	public ResponseEntity<AcademicPeriodResponse> getPeriod(@PathVariable UUID id) {
		PeriodResult result = getAcademicPeriodUseCase.getById(id);
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping
	public ResponseEntity<AcademicPeriodListResponse> listPeriods(@RequestParam(required = false) PeriodStatus status,
			@RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		ListAcademicPeriodsResult result = listAcademicPeriodsUseCase
				.listPeriods(new ListAcademicPeriodsQuery(status, search, page, size));
		return ResponseEntity.ok(new AcademicPeriodListResponse(
				result.items().stream().map(AcademicPeriodController::toItem).toList(), result.totalElements(),
				result.totalPages(), result.page(), result.size()));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<AcademicPeriodResponse> changeStatus(@PathVariable UUID id,
			@Valid @RequestBody ChangePeriodStatusRequest request) {
		PeriodResult result = changeAcademicPeriodStatusUseCase
				.changeStatus(new ChangeStatusCommand(currentUserId(), id, request.status()));
		return ResponseEntity.ok(toResponse(result));
	}

	@PostMapping("/advance-by-date")
	public ResponseEntity<AdvancePeriodsByDateResponse> advanceByDate() {
		int advanced = advanceAcademicPeriodStatusByDateUseCase.advanceAll(LocalDate.now());
		return ResponseEntity.ok(new AdvancePeriodsByDateResponse(advanced));
	}

	/**
	 * Extracts the acting user's id from the JWT principal — same mechanism
	 * as {@code SubjectClassificationController#currentUserId}. Only
	 * consumed by {@link ChangeStatusCommand#callerId()}, reserved for future
	 * audit-log attribution and not read by the use case yet.
	 */
	private static UUID currentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return UUID.fromString(authentication.getName());
	}

	private static AcademicPeriodResponse toResponse(PeriodResult result) {
		return new AcademicPeriodResponse(result.id(), result.name(), result.year(), result.periodNumber(),
				result.type(), result.startDate(), result.endDate(), result.enrollmentStart(), result.enrollmentEnd(),
				result.status());
	}

	private static AcademicPeriodListItemResponse toItem(PeriodSummary summary) {
		return new AcademicPeriodListItemResponse(summary.id(), summary.name(), summary.year(), summary.periodNumber(),
				summary.type(), summary.startDate(), summary.endDate(), summary.enrollmentStart(),
				summary.enrollmentEnd(), summary.status());
	}
}
