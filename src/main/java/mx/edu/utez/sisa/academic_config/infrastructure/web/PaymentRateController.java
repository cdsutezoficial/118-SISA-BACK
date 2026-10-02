package mx.edu.utez.sisa.academic_config.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentRateDraft;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentRatesUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ReconcilePaymentRatesUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ReconcilePaymentRatesUseCase.PaymentRateResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.ReconcilePaymentRatesUseCase.ReconcilePaymentRatesCommand;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.PaymentRateListResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.PaymentRateResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.ReconcilePaymentRatesRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Thin controller for {@code PaymentRate} — nested under its owning
 * {@code PaymentConcept} at {@code /payment-concepts/{conceptId}/rates},
 * mirroring {@code AcademicPlanController}'s nested-path style for
 * {@code PlanLevel}/{@code Subject} even though {@code PaymentRate}'s
 * persistence is its own repository rather than encapsulated in the parent
 * aggregate.
 *
 * <p>
 * The write verb is {@code PUT}, not {@code POST}, because the body is the
 * concept's COMPLETE rate set rather than one new row: the client replaces what
 * it knows to be true and the server works out the difference. A {@code POST}
 * per rate, as this used to be, gave the client no way to express "this
 * destination no longer has a price" and no way to be atomic across careers.
 *
 * <p>
 * There is still no delete endpoint. Withdrawing a price is done by omitting the
 * destination from the set, which deactivates the row and keeps it in the
 * history; for a {@code PERIODIC_QUOTA} concept that omission is refused, since
 * every active career must have a price.
 *
 * <p>Role authorization (ADMIN or PERSONAL_FINANZAS, same pair as
 * {@code PaymentConcept}) is enforced by {@code SecurityFilterConfig}'s
 * {@code /payment-concepts} matchers, not here.
 */
@RestController
@RequestMapping("/payment-concepts/{conceptId}/rates")
public class PaymentRateController {

	private final ReconcilePaymentRatesUseCase reconcilePaymentRatesUseCase;

	private final ListPaymentRatesUseCase listPaymentRatesUseCase;

	public PaymentRateController(ReconcilePaymentRatesUseCase reconcilePaymentRatesUseCase,
			ListPaymentRatesUseCase listPaymentRatesUseCase) {
		this.reconcilePaymentRatesUseCase = reconcilePaymentRatesUseCase;
		this.listPaymentRatesUseCase = listPaymentRatesUseCase;
	}

	@PutMapping
	public ResponseEntity<PaymentRateListResponse> reconcileRates(@PathVariable UUID conceptId,
			@Valid @RequestBody ReconcilePaymentRatesRequest request) {
		List<PaymentRateDraft> drafts = request.rates() == null ? List.of()
				: request.rates().stream().map(PaymentRateController::toDraft).toList();
		ReconcilePaymentRatesCommand command = new ReconcilePaymentRatesCommand(conceptId, drafts);

		return ResponseEntity.ok(new PaymentRateListResponse(
				reconcilePaymentRatesUseCase.reconcileRates(command).stream().map(PaymentRateController::toResponse)
						.toList()));
	}

	@GetMapping
	public ResponseEntity<PaymentRateListResponse> listRates(@PathVariable UUID conceptId) {
		return ResponseEntity.ok(new PaymentRateListResponse(
				listPaymentRatesUseCase.listRates(conceptId).stream().map(PaymentRateController::toResponse).toList()));
	}

	private static PaymentRateDraft toDraft(ReconcilePaymentRatesRequest.PaymentRateDraftRequest draft) {
		return new PaymentRateDraft(draft.programId(), draft.level(), draft.amount(), draft.periodId());
	}

	private static PaymentRateResponse toResponse(PaymentRateResult result) {
		return new PaymentRateResponse(result.id(), result.conceptId(), result.programId(), result.level(),
				result.amount(), result.periodId(), result.status(), result.createdAt());
	}
}