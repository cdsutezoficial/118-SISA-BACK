package mx.edu.utez.sisa.academic_config.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangePaymentConceptStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangePaymentConceptStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.CreatePaymentConceptCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentConceptResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetPaymentConceptUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentConceptsUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentConceptsUseCase.ListPaymentConceptsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentConceptsUseCase.ListPaymentConceptsResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentConceptsUseCase.PaymentConceptSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdatePaymentConceptUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdatePaymentConceptUseCase.UpdatePaymentConceptCommand;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.ChangePaymentConceptStatusRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.CreatePaymentConceptRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.PaymentConceptListItemResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.PaymentConceptListResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.PaymentConceptResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.UpdatePaymentConceptRequest;
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
 * Thin controller for {@code PaymentConcept} — Fase 1 of 4 of "Conceptos de
 * Pago" ({@code docs/plans/2026-07-28-payment-concept.md}): {@code POST /payment-concepts}
 * (201), {@code GET /payment-concepts} (paginated),
 * {@code GET /payment-concepts/{id}} (404 if missing),
 * {@code PUT /payment-concepts/{id}} (404 if missing), and
 * {@code PATCH /payment-concepts/{id}/status} (404 if missing). Role
 * authorization is enforced by {@code identity.SecurityFilterConfig}'s
 * {@code /payment-concepts} matchers — {@code ADMIN}/{@code PERSONAL_FINANZAS},
 * deliberately NOT {@code SERVICIOS_ESCOLARES} (unlike every other
 * {@code academic_config} aggregate; see the plan's section 6 and the
 * security config's Javadoc).
 */
@RestController
@RequestMapping("/payment-concepts")
public class PaymentConceptController {

	private final ListPaymentConceptsUseCase listPaymentConceptsUseCase;

	private final CreatePaymentConceptUseCase createPaymentConceptUseCase;

	private final GetPaymentConceptUseCase getPaymentConceptUseCase;

	private final UpdatePaymentConceptUseCase updatePaymentConceptUseCase;

	private final ChangePaymentConceptStatusUseCase changePaymentConceptStatusUseCase;

	public PaymentConceptController(ListPaymentConceptsUseCase listPaymentConceptsUseCase,
			CreatePaymentConceptUseCase createPaymentConceptUseCase,
			GetPaymentConceptUseCase getPaymentConceptUseCase,
			UpdatePaymentConceptUseCase updatePaymentConceptUseCase,
			ChangePaymentConceptStatusUseCase changePaymentConceptStatusUseCase) {
		this.listPaymentConceptsUseCase = listPaymentConceptsUseCase;
		this.createPaymentConceptUseCase = createPaymentConceptUseCase;
		this.getPaymentConceptUseCase = getPaymentConceptUseCase;
		this.updatePaymentConceptUseCase = updatePaymentConceptUseCase;
		this.changePaymentConceptStatusUseCase = changePaymentConceptStatusUseCase;
	}

	@PostMapping
	public ResponseEntity<PaymentConceptResponse> createPaymentConcept(
			@Valid @RequestBody CreatePaymentConceptRequest request) {
		PaymentConceptResult result = createPaymentConceptUseCase
				.createPaymentConcept(new CreatePaymentConceptCommand(request.name(), request.description(),
						request.policies(), request.type(), request.isTuition(), request.isStandalone(),
						request.maxPerStudent(), request.maxPerPeriod(), request.requiresValidation(),
						request.availableFrom(), request.availableUntil(), request.areaId(), request.cost(),
						request.isExternal(), request.costExternal(), request.isAccumulable(),
						request.isMulticoncept(), request.quotaLimit(), request.linkedConceptIds(),
						request.programIds()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
	}

	@PutMapping("/{id}")
	public ResponseEntity<PaymentConceptResponse> updatePaymentConcept(@PathVariable UUID id,
			@Valid @RequestBody UpdatePaymentConceptRequest request) {
		PaymentConceptResult result = updatePaymentConceptUseCase
				.updatePaymentConcept(new UpdatePaymentConceptCommand(id, request.name(), request.description(),
						request.policies(), request.type(), request.isTuition(), request.isStandalone(),
						request.maxPerStudent(), request.maxPerPeriod(), request.requiresValidation(),
						request.availableFrom(), request.availableUntil(), request.areaId(), request.cost(),
						request.isExternal(), request.costExternal(), request.isAccumulable(),
						request.isMulticoncept(), request.quotaLimit(), request.linkedConceptIds(),
						request.programIds()));
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping("/{id}")
	public ResponseEntity<PaymentConceptResponse> getPaymentConcept(@PathVariable UUID id) {
		PaymentConceptResult result = getPaymentConceptUseCase.getById(id);
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping
	public ResponseEntity<PaymentConceptListResponse> listPaymentConcepts(
			@RequestParam(required = false) PaymentConceptStatus status,
			@RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		ListPaymentConceptsResult result = listPaymentConceptsUseCase
				.listPaymentConcepts(new ListPaymentConceptsQuery(status, search, page, size));
		return ResponseEntity.ok(new PaymentConceptListResponse(
				result.items().stream().map(PaymentConceptController::toItem).toList(), result.totalElements(),
				result.totalPages(), result.page(), result.size()));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<PaymentConceptResponse> changeStatus(@PathVariable UUID id,
			@Valid @RequestBody ChangePaymentConceptStatusRequest request) {
		PaymentConceptResult result = changePaymentConceptStatusUseCase
				.changeStatus(new ChangeStatusCommand(currentUserId(), id, request.status()));
		return ResponseEntity.ok(toResponse(result));
	}

	/**
	 * Extracts the acting user's id from the JWT principal — same mechanism as
	 * {@code SubjectClassificationController#currentUserId}. Only consumed by
	 * {@link ChangeStatusCommand#callerId()}, which is reserved for future
	 * audit-log attribution and not read by the use case yet.
	 */
	private static UUID currentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return UUID.fromString(authentication.getName());
	}

	private static PaymentConceptResponse toResponse(PaymentConceptResult result) {
		return new PaymentConceptResponse(result.id(), result.name(), result.description(), result.policies(),
				result.type(), result.isTuition(), result.isStandalone(), result.maxPerStudent(),
				result.maxPerPeriod(), result.requiresValidation(), result.availableFrom(), result.availableUntil(),
				result.status(), result.areaId(), result.cost(), result.isExternal(), result.costExternal(),
				result.isAccumulable(), result.isMulticoncept(), result.quotaLimit(), result.linkedConceptIds(),
				result.programIds());
	}

	private static PaymentConceptListItemResponse toItem(PaymentConceptSummary summary) {
		return new PaymentConceptListItemResponse(summary.id(), summary.name(), summary.type(),
				summary.isTuition(), summary.isStandalone(), summary.status());
	}
}
