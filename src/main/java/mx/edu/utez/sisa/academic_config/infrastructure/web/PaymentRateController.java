package mx.edu.utez.sisa.academic_config.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentRatesUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.SetPaymentRateUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.SetPaymentRateUseCase.PaymentRateResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.SetPaymentRateUseCase.SetPaymentRateCommand;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.CreatePaymentRateRequest;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.PaymentRateListResponse;
import mx.edu.utez.sisa.academic_config.infrastructure.web.dto.PaymentRateResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Thin controller for {@code PaymentRate} — Fase 2 of 4 of "Conceptos de
 * Pago" (plan: {@code docs/plans/2026-07-28-payment-rate.md}): nested under
 * its owning {@code PaymentConcept} at {@code /payment-concepts/{conceptId}/rates},
 * mirroring {@code AcademicPlanController}'s nested-path style for
 * {@code PlanLevel}/{@code Subject} even though {@code PaymentRate}'s
 * persistence is its own repository rather than encapsulated in the parent
 * aggregate (plan section 5). Only {@code POST} (201) and {@code GET}
 * (full history, no pagination) exist — no Update/Delete, by design (plan
 * section 4: append-only history). Role authorization (ADMIN or
 * PERSONAL_FINANZAS, same pair as {@code PaymentConcept}) is enforced by
 * {@code identity.SecurityFilterConfig}'s {@code /payment-concepts} matchers,
 * not here.
 */
@RestController
@RequestMapping("/payment-concepts/{conceptId}/rates")
public class PaymentRateController {

	private final SetPaymentRateUseCase setPaymentRateUseCase;

	private final ListPaymentRatesUseCase listPaymentRatesUseCase;

	public PaymentRateController(SetPaymentRateUseCase setPaymentRateUseCase,
			ListPaymentRatesUseCase listPaymentRatesUseCase) {
		this.setPaymentRateUseCase = setPaymentRateUseCase;
		this.listPaymentRatesUseCase = listPaymentRatesUseCase;
	}

	@PostMapping
	public ResponseEntity<PaymentRateResponse> setRate(@PathVariable UUID conceptId,
			@Valid @RequestBody CreatePaymentRateRequest request) {
		PaymentRateResult result = setPaymentRateUseCase.setRate(new SetPaymentRateCommand(conceptId,
				request.programId(), request.level(), request.amount(), request.periodId(), request.validFrom()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
	}

	@GetMapping
	public ResponseEntity<PaymentRateListResponse> listRates(@PathVariable UUID conceptId) {
		return ResponseEntity.ok(new PaymentRateListResponse(
				listPaymentRatesUseCase.listRates(conceptId).stream().map(PaymentRateController::toResponse).toList()));
	}

	private static PaymentRateResponse toResponse(PaymentRateResult result) {
		return new PaymentRateResponse(result.id(), result.conceptId(), result.programId(), result.level(),
				result.amount(), result.periodId(), result.validFrom(), result.validTo());
	}
}
