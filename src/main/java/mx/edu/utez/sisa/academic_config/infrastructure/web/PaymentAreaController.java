package mx.edu.utez.sisa.academic_config.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentAreaStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangePaymentAreaStatusUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangePaymentAreaStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentAreaUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentAreaUseCase.CreatePaymentAreaCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentAreaUseCase.PaymentAreaResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.GetPaymentAreaUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentAreasUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentAreasUseCase.ListPaymentAreasQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentAreasUseCase.ListPaymentAreasResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentAreasUseCase.PaymentAreaSummary;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdatePaymentAreaUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdatePaymentAreaUseCase.UpdatePaymentAreaCommand;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.PaymentAreaJpaRepository;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.ChangePaymentAreaStatusRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.CreatePaymentAreaRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.PaymentAreaListItemResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.PaymentAreaListResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.PaymentAreaResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.UpdatePaymentAreaRequest;
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
 * Thin controller for {@code PaymentArea} CRUD (frontend "Áreas de
 * facturación"): {@code POST /payment-areas} (201),
 * {@code PUT /payment-areas/{id}}, {@code GET /payment-areas} (paginated),
 * {@code GET /payment-areas/{id}}, {@code GET /payment-areas/options},
 * {@code PATCH /payment-areas/{id}/status}. Role authorization (ADMIN or
 * PERSONAL_FINANZAS, same as {@code PaymentConcept}) is enforced by
 * {@code identity.SecurityFilterConfig}'s split {@code /payment-areas}
 * matchers, not here.
 */
@RestController
@RequestMapping("/payment-areas")
public class PaymentAreaController {

	private final CreatePaymentAreaUseCase createPaymentAreaUseCase;

	private final UpdatePaymentAreaUseCase updatePaymentAreaUseCase;

	private final ListPaymentAreasUseCase listPaymentAreasUseCase;

	private final ChangePaymentAreaStatusUseCase changePaymentAreaStatusUseCase;

	private final GetPaymentAreaUseCase getPaymentAreaUseCase;

	private final PaymentAreaJpaRepository paymentAreaJpaRepository;

	public PaymentAreaController(CreatePaymentAreaUseCase createPaymentAreaUseCase,
			UpdatePaymentAreaUseCase updatePaymentAreaUseCase, ListPaymentAreasUseCase listPaymentAreasUseCase,
			ChangePaymentAreaStatusUseCase changePaymentAreaStatusUseCase,
			GetPaymentAreaUseCase getPaymentAreaUseCase, PaymentAreaJpaRepository paymentAreaJpaRepository) {
		this.createPaymentAreaUseCase = createPaymentAreaUseCase;
		this.updatePaymentAreaUseCase = updatePaymentAreaUseCase;
		this.listPaymentAreasUseCase = listPaymentAreasUseCase;
		this.changePaymentAreaStatusUseCase = changePaymentAreaStatusUseCase;
		this.getPaymentAreaUseCase = getPaymentAreaUseCase;
		this.paymentAreaJpaRepository = paymentAreaJpaRepository;
	}

	@PostMapping
	public ResponseEntity<PaymentAreaResponse> createArea(@Valid @RequestBody CreatePaymentAreaRequest request) {
		PaymentAreaResult result = createPaymentAreaUseCase.createPaymentArea(
				new CreatePaymentAreaCommand(request.name(), request.code(), request.description()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
	}

	@PutMapping("/{id}")
	public ResponseEntity<PaymentAreaResponse> updateArea(@PathVariable UUID id,
			@Valid @RequestBody UpdatePaymentAreaRequest request) {
		PaymentAreaResult result = updatePaymentAreaUseCase.updatePaymentArea(
				new UpdatePaymentAreaCommand(id, request.name(), request.code(), request.description()));
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping("/{id}")
	public ResponseEntity<PaymentAreaResponse> getArea(@PathVariable UUID id) {
		PaymentAreaResult result = getPaymentAreaUseCase.getById(id);
		return ResponseEntity.ok(toResponse(result));
	}

	@GetMapping
	public ResponseEntity<PaymentAreaListResponse> listAreas(
			@RequestParam(required = false) PaymentAreaStatus status, @RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		ListPaymentAreasResult result = listPaymentAreasUseCase
				.listPaymentAreas(new ListPaymentAreasQuery(status, search, page, size));
		return ResponseEntity.ok(new PaymentAreaListResponse(
				result.items().stream().map(PaymentAreaController::toItem).toList(), result.totalElements(),
				result.totalPages(), result.page(), result.size()));
	}

	@GetMapping("/options")
	public List<OptionResponse> listAreaOptions() {
		return paymentAreaJpaRepository.findByStatusOrderByNameAsc(PaymentAreaStatus.ACTIVE).stream()
				.map(a -> new OptionResponse(a.getId(), a.getName(), a.getCode())).toList();
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<PaymentAreaResponse> changeStatus(@PathVariable UUID id,
			@Valid @RequestBody ChangePaymentAreaStatusRequest request) {
		PaymentAreaResult result = changePaymentAreaStatusUseCase
				.changeStatus(new ChangeStatusCommand(currentUserId(), id, request.status()));
		return ResponseEntity.ok(toResponse(result));
	}

	/**
	 * Extracts the acting user's id from the JWT principal — same mechanism as
	 * {@code AcademicDivisionController#currentUserId}. Only consumed by
	 * {@link ChangeStatusCommand#callerId()}, which is reserved for future
	 * audit-log attribution and not read by the use case yet.
	 */
	private static UUID currentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return UUID.fromString(authentication.getName());
	}

	private static PaymentAreaResponse toResponse(PaymentAreaResult result) {
		return new PaymentAreaResponse(result.id(), result.name(), result.code(), result.description(),
				result.status());
	}

	private static PaymentAreaListItemResponse toItem(PaymentAreaSummary summary) {
		return new PaymentAreaListItemResponse(summary.id(), summary.name(), summary.code(), summary.description(),
				summary.status());
	}
}
