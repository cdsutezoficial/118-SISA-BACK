package mx.edu.utez.sisa.academic_config.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.academic_config.domain.model.PlanStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.AddPlanLevelUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.AddPlanLevelUseCase.AddPlanLevelCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.AddSubjectToPlanUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.AddSubjectToPlanUseCase.AddSubjectCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicPlanStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicPlanStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.AcademicPlanResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.CreateAcademicPlanCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.GradeScaleEntryResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.GradeScaleResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.PlanLevelResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.SubjectResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetAcademicPlanUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPlansUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPlansUseCase.ListAcademicPlansQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPlansUseCase.ListAcademicPlansResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListAcademicPlansUseCase.PlanSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.RemoveGradeScaleUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.RemoveGradeScaleUseCase.RemoveGradeScaleCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.RemovePlanLevelUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.RemovePlanLevelUseCase.RemovePlanLevelCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.RemoveSubjectUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.RemoveSubjectUseCase.RemoveSubjectCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.SetGradeScaleUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.SetGradeScaleUseCase.GradeScaleEntryCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.SetGradeScaleUseCase.SetGradeScaleCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicPlanUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicPlanUseCase.UpdateAcademicPlanCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateGradeScaleUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateGradeScaleUseCase.UpdateGradeScaleCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdatePlanLevelUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdatePlanLevelUseCase.UpdatePlanLevelCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateSubjectUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateSubjectUseCase.UpdateSubjectCommand;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.AcademicPlanListItemResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.AcademicPlanListResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.AcademicPlanResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.AddPlanLevelRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.AddSubjectRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.ChangePlanStatusRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.CreateAcademicPlanRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.GradeScaleEntryRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.GradeScaleEntryResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.GradeScaleResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.PlanLevelResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.SetGradeScaleRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.SubjectResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.UpdateAcademicPlanRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.UpdatePlanLevelRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.UpdateSubjectRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * Thin controller for {@code AcademicPlan} CRUD plus its owned
 * {@code PlanLevel}/{@code Subject} child operations (spec: "Academic Plan
 * Management", design.md — "REST shape — nested paths, child-scoped
 * payloads"): {@code POST/PUT/GET /plans}, {@code GET /plans} (paginated),
 * {@code PATCH /plans/{id}/status}, plus 6 nested level/subject endpoints.
 * Role authorization (ADMIN or SERVICIOS_ESCOLARES on every verb) is
 * enforced by {@code identity.SecurityFilterConfig}'s {@code /plans}
 * matchers, not here — mirrors {@code AcademicProgramController}. Child
 * endpoints return only the child DTO, not the whole plan (design.md —
 * Decision: "REST shape") — {@code GET /plans/{id}} is the only endpoint
 * returning the full nested tree.
 */
@RestController
@RequestMapping("/plans")
public class AcademicPlanController {

	private final CreateAcademicPlanUseCase createAcademicPlanUseCase;

	private final UpdateAcademicPlanUseCase updateAcademicPlanUseCase;

	private final ListAcademicPlansUseCase listAcademicPlansUseCase;

	private final GetAcademicPlanUseCase getAcademicPlanUseCase;

	private final ChangeAcademicPlanStatusUseCase changeAcademicPlanStatusUseCase;

	private final AddPlanLevelUseCase addPlanLevelUseCase;

	private final UpdatePlanLevelUseCase updatePlanLevelUseCase;

	private final RemovePlanLevelUseCase removePlanLevelUseCase;

	private final AddSubjectToPlanUseCase addSubjectToPlanUseCase;

	private final UpdateSubjectUseCase updateSubjectUseCase;

	private final RemoveSubjectUseCase removeSubjectUseCase;

	private final SetGradeScaleUseCase setGradeScaleUseCase;

	private final UpdateGradeScaleUseCase updateGradeScaleUseCase;

	private final RemoveGradeScaleUseCase removeGradeScaleUseCase;

	public AcademicPlanController(CreateAcademicPlanUseCase createAcademicPlanUseCase,
			UpdateAcademicPlanUseCase updateAcademicPlanUseCase, ListAcademicPlansUseCase listAcademicPlansUseCase,
			GetAcademicPlanUseCase getAcademicPlanUseCase,
			ChangeAcademicPlanStatusUseCase changeAcademicPlanStatusUseCase, AddPlanLevelUseCase addPlanLevelUseCase,
			UpdatePlanLevelUseCase updatePlanLevelUseCase, RemovePlanLevelUseCase removePlanLevelUseCase,
			AddSubjectToPlanUseCase addSubjectToPlanUseCase, UpdateSubjectUseCase updateSubjectUseCase,
			RemoveSubjectUseCase removeSubjectUseCase, SetGradeScaleUseCase setGradeScaleUseCase,
			UpdateGradeScaleUseCase updateGradeScaleUseCase, RemoveGradeScaleUseCase removeGradeScaleUseCase) {
		this.createAcademicPlanUseCase = createAcademicPlanUseCase;
		this.updateAcademicPlanUseCase = updateAcademicPlanUseCase;
		this.listAcademicPlansUseCase = listAcademicPlansUseCase;
		this.getAcademicPlanUseCase = getAcademicPlanUseCase;
		this.changeAcademicPlanStatusUseCase = changeAcademicPlanStatusUseCase;
		this.addPlanLevelUseCase = addPlanLevelUseCase;
		this.updatePlanLevelUseCase = updatePlanLevelUseCase;
		this.removePlanLevelUseCase = removePlanLevelUseCase;
		this.addSubjectToPlanUseCase = addSubjectToPlanUseCase;
		this.updateSubjectUseCase = updateSubjectUseCase;
		this.removeSubjectUseCase = removeSubjectUseCase;
		this.setGradeScaleUseCase = setGradeScaleUseCase;
		this.updateGradeScaleUseCase = updateGradeScaleUseCase;
		this.removeGradeScaleUseCase = removeGradeScaleUseCase;
	}

	@PostMapping
	public ResponseEntity<AcademicPlanResponse> createPlan(@Valid @RequestBody CreateAcademicPlanRequest request) {
		AcademicPlanResult result = createAcademicPlanUseCase.createPlan(new CreateAcademicPlanCommand(
				request.programId(), request.version(), request.validityPeriod(), request.titulationKey(),
				request.effectiveFrom(), request.totalLevels(), request.minPassingGrade(),
				request.maxExtraordinaryExamsPerPeriod(), request.requiresSocialService(),
				request.socialServiceMinLevelId()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
	}

	@PutMapping("/{id}")
	public ResponseEntity<AcademicPlanResponse> updatePlan(@PathVariable UUID id,
			@Valid @RequestBody UpdateAcademicPlanRequest request) {
		AcademicPlanResult result = updateAcademicPlanUseCase.updatePlan(new UpdateAcademicPlanCommand(id,
				request.version(), request.validityPeriod(), request.titulationKey(), request.effectiveFrom(),
				request.totalLevels(), request.minPassingGrade(), request.maxExtraordinaryExamsPerPeriod(),
				request.requiresSocialService(), request.socialServiceMinLevelId()));
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping("/{id}")
	public ResponseEntity<AcademicPlanResponse> getPlan(@PathVariable UUID id) {
		AcademicPlanResult result = getAcademicPlanUseCase.getById(id);
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping
	public ResponseEntity<AcademicPlanListResponse> listPlans(@RequestParam(required = false) UUID programId,
			@RequestParam(required = false) PlanStatus status, @RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		ListAcademicPlansResult result = listAcademicPlansUseCase
				.listPlans(new ListAcademicPlansQuery(status, search, programId, page, size));
		return ResponseEntity.ok(new AcademicPlanListResponse(
				result.items().stream().map(AcademicPlanController::toItem).toList(), result.totalElements(),
				result.totalPages(), result.page(), result.size()));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<AcademicPlanResponse> changeStatus(@PathVariable UUID id,
			@Valid @RequestBody ChangePlanStatusRequest request) {
		AcademicPlanResult result = changeAcademicPlanStatusUseCase
				.changeStatus(new ChangeStatusCommand(currentUserId(), id, request.status()));
		return ResponseEntity.ok(toResponse(result));
	}

	@PostMapping("/{id}/levels")
	public ResponseEntity<PlanLevelResponse> addLevel(@PathVariable UUID id,
			@Valid @RequestBody AddPlanLevelRequest request) {
		PlanLevelResult result = addPlanLevelUseCase
				.addLevel(new AddPlanLevelCommand(id, request.levelNumber(), request.type(), request.description()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toLevelResponse(result));
	}

	@PutMapping("/{id}/levels/{levelId}")
	public ResponseEntity<PlanLevelResponse> updateLevel(@PathVariable UUID id, @PathVariable UUID levelId,
			@Valid @RequestBody UpdatePlanLevelRequest request) {
		PlanLevelResult result = updatePlanLevelUseCase.updateLevel(
				new UpdatePlanLevelCommand(id, levelId, request.levelNumber(), request.type(), request.description()));
		return ResponseEntity.ok(toLevelResponse(result));
	}

	@DeleteMapping("/{id}/levels/{levelId}")
	public ResponseEntity<Void> removeLevel(@PathVariable UUID id, @PathVariable UUID levelId) {
		removePlanLevelUseCase.removeLevel(new RemovePlanLevelCommand(id, levelId));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/levels/{levelId}/subjects")
	public ResponseEntity<SubjectResponse> addSubject(@PathVariable UUID id, @PathVariable UUID levelId,
			@Valid @RequestBody AddSubjectRequest request) {
		SubjectResult result = addSubjectToPlanUseCase.addSubject(new AddSubjectCommand(id, levelId, request.code(),
				request.name(), request.credits(), request.weeklyHours(), request.evaluationUnits(),
				request.displayOrder(), request.type(), defaultRetakeable(request.isRetakeable()),
				request.classificationId()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toSubjectResponse(result));
	}

	@PutMapping("/{id}/levels/{levelId}/subjects/{subjectId}")
	public ResponseEntity<SubjectResponse> updateSubject(@PathVariable UUID id, @PathVariable UUID levelId,
			@PathVariable UUID subjectId, @Valid @RequestBody UpdateSubjectRequest request) {
		SubjectResult result = updateSubjectUseCase.updateSubject(new UpdateSubjectCommand(id, subjectId,
				request.code(), request.name(), request.credits(), request.weeklyHours(), request.evaluationUnits(),
				request.displayOrder(), request.type(), defaultRetakeable(request.isRetakeable()),
				request.classificationId()));
		return ResponseEntity.ok(toSubjectResponse(result));
	}

	@DeleteMapping("/{id}/levels/{levelId}/subjects/{subjectId}")
	public ResponseEntity<Void> removeSubject(@PathVariable UUID id, @PathVariable UUID levelId,
			@PathVariable UUID subjectId) {
		removeSubjectUseCase.removeSubject(new RemoveSubjectCommand(id, subjectId));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/grade-scales")
	public ResponseEntity<GradeScaleResponse> setGradeScale(@PathVariable UUID id,
			@Valid @RequestBody SetGradeScaleRequest request) {
		GradeScaleResult result = setGradeScaleUseCase.setGradeScale(new SetGradeScaleCommand(id,
				request.classificationId(), request.numericMin(), request.numericMax(),
				toEntryCommands(request.entries())));
		return ResponseEntity.status(HttpStatus.CREATED).body(toGradeScaleResponse(result));
	}

	@PutMapping("/{id}/grade-scales/{scaleId}")
	public ResponseEntity<GradeScaleResponse> updateGradeScale(@PathVariable UUID id, @PathVariable UUID scaleId,
			@Valid @RequestBody SetGradeScaleRequest request) {
		GradeScaleResult result = updateGradeScaleUseCase.updateGradeScale(new UpdateGradeScaleCommand(id, scaleId,
				request.classificationId(), request.numericMin(), request.numericMax(),
				toEntryCommands(request.entries())));
		return ResponseEntity.ok(toGradeScaleResponse(result));
	}

	@DeleteMapping("/{id}/grade-scales/{scaleId}")
	public ResponseEntity<Void> removeGradeScale(@PathVariable UUID id, @PathVariable UUID scaleId) {
		removeGradeScaleUseCase.removeGradeScale(new RemoveGradeScaleCommand(id, scaleId));
		return ResponseEntity.noContent().build();
	}

	/**
	 * Extracts the acting user's id from the JWT principal — see
	 * {@code AcademicProgramController#currentUserId()} for the identical
	 * rationale. Only consumed by {@link ChangeStatusCommand#callerId()},
	 * reserved for future audit-log attribution.
	 */
	private static UUID currentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return UUID.fromString(authentication.getName());
	}

	/**
	 * {@code isRetakeable} defaults to {@code true} when omitted from the
	 * request body (spec: "isRetakeable default true") — a primitive
	 * {@code boolean} field on the request record would silently default to
	 * {@code false} instead, so the nullable {@link Boolean} wrapper plus
	 * this explicit default is required.
	 */
	private static boolean defaultRetakeable(Boolean isRetakeable) {
		return isRetakeable == null || isRetakeable;
	}

	private static AcademicPlanResponse toResponse(AcademicPlanResult result) {
		return new AcademicPlanResponse(result.id(), result.programId(), result.version(), result.validityPeriod(),
				result.titulationKey(), result.effectiveFrom(), result.totalLevels(), result.minPassingGrade(),
				result.maxExtraordinaryExamsPerPeriod(), result.requiresSocialService(),
				result.socialServiceMinLevelId(), result.status(),
				result.levels().stream().map(AcademicPlanController::toLevelResponse).toList(),
				result.gradeScales().stream().map(AcademicPlanController::toGradeScaleResponse).toList());
	}

	private static AcademicPlanListItemResponse toItem(PlanSummary summary) {
		return new AcademicPlanListItemResponse(summary.id(), summary.programId(), summary.version(),
				summary.validityPeriod(), summary.effectiveFrom(), summary.totalLevels(), summary.status());
	}

	private static PlanLevelResponse toLevelResponse(PlanLevelResult result) {
		return new PlanLevelResponse(result.id(), result.levelNumber(), result.type(), result.description(),
				result.subjects().stream().map(AcademicPlanController::toSubjectResponse).toList());
	}

	private static SubjectResponse toSubjectResponse(SubjectResult result) {
		return new SubjectResponse(result.id(), result.code(), result.name(), result.credits(), result.weeklyHours(),
				result.evaluationUnits(), result.displayOrder(), result.type(), result.isRetakeable(),
				result.classificationId());
	}

	private static GradeScaleResponse toGradeScaleResponse(GradeScaleResult result) {
		return new GradeScaleResponse(result.id(), result.classificationId(), result.numericMin(), result.numericMax(),
				result.entries().stream().map(AcademicPlanController::toGradeScaleEntryResponse).toList());
	}

	private static GradeScaleEntryResponse toGradeScaleEntryResponse(GradeScaleEntryResult result) {
		return new GradeScaleEntryResponse(result.id(), result.fromValue(), result.toValue(), result.letter(),
				result.description(), result.passed());
	}

	private static List<GradeScaleEntryCommand> toEntryCommands(List<GradeScaleEntryRequest> entries) {
		return entries.stream().map(entry -> new GradeScaleEntryCommand(entry.fromValue(), entry.toValue(),
				entry.letter(), entry.description(), entry.passed())).toList();
	}
}
