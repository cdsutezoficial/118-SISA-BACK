package mx.edu.utez.sisa.academic_config.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicProgramStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicProgramStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase.AcademicProgramResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase.CreateAcademicProgramCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetAcademicProgramUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicProgramsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicProgramsUseCase.ListAcademicProgramsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicProgramsUseCase.ListAcademicProgramsResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicProgramsUseCase.ProgramSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicProgramUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicProgramUseCase.UpdateAcademicProgramCommand;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.AcademicProgramJpaRepository;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.AcademicProgramListItemResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.AcademicProgramListResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.AcademicProgramResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.ChangeProgramStatusRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.CreateAcademicProgramRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.UpdateAcademicProgramRequest;
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
 * Thin controller for {@code AcademicProgram} CRUD (spec: "Academic Program
 * Management"): {@code POST /programs} (201), {@code PUT /programs/{id}},
 * {@code GET /programs} (paginated), {@code GET /programs/{id}},
 * {@code PATCH /programs/{id}/status}. Role authorization (ADMIN or
 * SERVICIOS_ESCOLARES on every verb) is enforced by
 * {@code identity.SecurityFilterConfig}'s split {@code /programs} matchers,
 * not here — mirrors {@code AcademicDivisionController}.
 */
@RestController
@RequestMapping("/programs")
public class AcademicProgramController {

	private final CreateAcademicProgramUseCase createAcademicProgramUseCase;

	private final UpdateAcademicProgramUseCase updateAcademicProgramUseCase;

	private final ListAcademicProgramsUseCase listAcademicProgramsUseCase;

	private final GetAcademicProgramUseCase getAcademicProgramUseCase;

	private final ChangeAcademicProgramStatusUseCase changeAcademicProgramStatusUseCase;

	private final AcademicProgramJpaRepository academicProgramJpaRepository;

	public AcademicProgramController(CreateAcademicProgramUseCase createAcademicProgramUseCase,
			UpdateAcademicProgramUseCase updateAcademicProgramUseCase,
			ListAcademicProgramsUseCase listAcademicProgramsUseCase, GetAcademicProgramUseCase getAcademicProgramUseCase,
			ChangeAcademicProgramStatusUseCase changeAcademicProgramStatusUseCase,
			AcademicProgramJpaRepository academicProgramJpaRepository) {
		this.createAcademicProgramUseCase = createAcademicProgramUseCase;
		this.updateAcademicProgramUseCase = updateAcademicProgramUseCase;
		this.listAcademicProgramsUseCase = listAcademicProgramsUseCase;
		this.getAcademicProgramUseCase = getAcademicProgramUseCase;
		this.changeAcademicProgramStatusUseCase = changeAcademicProgramStatusUseCase;
		this.academicProgramJpaRepository = academicProgramJpaRepository;
	}

	@PostMapping
	public ResponseEntity<AcademicProgramResponse> createProgram(
			@Valid @RequestBody CreateAcademicProgramRequest request) {
		AcademicProgramResult result = createAcademicProgramUseCase.createProgram(new CreateAcademicProgramCommand(
				request.divisionId(), request.name(), request.offerName(), request.code(), request.level(),
				request.modality(), request.continuityProgramId(), request.description(), request.dgpCode()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
	}

	@PutMapping("/{id}")
	public ResponseEntity<AcademicProgramResponse> updateProgram(@PathVariable UUID id,
			@Valid @RequestBody UpdateAcademicProgramRequest request) {
		AcademicProgramResult result = updateAcademicProgramUseCase.updateProgram(new UpdateAcademicProgramCommand(id,
				request.divisionId(), request.name(), request.offerName(), request.code(), request.level(),
				request.modality(), request.continuityProgramId(), request.description(), request.dgpCode()));
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping("/{id}")
	public ResponseEntity<AcademicProgramResponse> getProgram(@PathVariable UUID id) {
		AcademicProgramResult result = getAcademicProgramUseCase.getById(id);
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping
	public ResponseEntity<AcademicProgramListResponse> listPrograms(@RequestParam(required = false) UUID divisionId,
			@RequestParam(required = false) ProgramStatus status, @RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		ListAcademicProgramsResult result = listAcademicProgramsUseCase
				.listPrograms(new ListAcademicProgramsQuery(status, search, divisionId, page, size));
		return ResponseEntity.ok(new AcademicProgramListResponse(
				result.items().stream().map(AcademicProgramController::toItem).toList(), result.totalElements(),
				result.totalPages(), result.page(), result.size()));
	}

	/**
	 * Reference-catalog read (transversal design: "Roles y Permisos — patrón
	 * reference"). Unlike {@code GET /programs}, this is a **minimal
	 * projection**: {@code ACTIVE} programs only, bare JSON array of
	 * {@code { id, label, code }} with no pagination, no management fields
	 * (description, dgpCode, status). It exists so pickers ("Programa" en
	 * {@code PlanForm}, {@code GruposForm}, {@code ConfiguracionAdmisionForm})
	 * can fill selects without pulling the full paged list. Read-only; no
	 * role filter here — it is deliberately the "reference" class
	 * ({@code authenticated()}) in {@code SecurityFilterConfig}, NOT the
	 * crippled-management projection. Optional {@code divisionId} cascades
	 * the picker to one division.
	 */
	@GetMapping("/options")
	public ResponseEntity<List<OptionResponse>> listProgramOptions(
			@RequestParam(required = false) UUID divisionId) {
		List<OptionResponse> items = (divisionId == null
				? academicProgramJpaRepository.findByStatusOrderByNameAsc(ProgramStatus.ACTIVE)
				: academicProgramJpaRepository.findByStatusAndDivisionIdOrderByNameAsc(ProgramStatus.ACTIVE, divisionId))
				.stream().map(p -> new OptionResponse(p.getId(), p.getName(), p.getCode())).toList();
		return ResponseEntity.ok(items);
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<AcademicProgramResponse> changeStatus(@PathVariable UUID id,
			@Valid @RequestBody ChangeProgramStatusRequest request) {
		AcademicProgramResult result = changeAcademicProgramStatusUseCase
				.changeStatus(new ChangeStatusCommand(currentUserId(), id, request.status()));
		return ResponseEntity.ok(toResponse(result));
	}

	/**
	 * Extracts the acting user's id from the JWT principal — see
	 * {@code AcademicDivisionController#currentUserId()} for the identical
	 * rationale. Only consumed by {@link ChangeStatusCommand#callerId()},
	 * reserved for future audit-log attribution.
	 */
	private static UUID currentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return UUID.fromString(authentication.getName());
	}

	private static AcademicProgramResponse toResponse(AcademicProgramResult result) {
		return new AcademicProgramResponse(result.id(), result.divisionId(), result.name(), result.offerName(),
				result.code(), result.level(), result.modality(), result.continuityProgramId(), result.description(),
				result.dgpCode(), result.status());
	}

	private static AcademicProgramListItemResponse toItem(ProgramSummary summary) {
		return new AcademicProgramListItemResponse(summary.id(), summary.divisionId(), summary.name(),
				summary.offerName(), summary.code(), summary.level(), summary.modality(), summary.description(),
				summary.dgpCode(), summary.status());
	}
}
