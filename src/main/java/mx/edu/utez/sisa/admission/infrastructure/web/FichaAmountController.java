package mx.edu.utez.sisa.admission.infrastructure.web;

import mx.edu.utez.sisa.admission.domain.port.in.GetFichaAmountUseCase;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.FichaAmountResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public quote of the admission-ficha price (Fase 11): the registration wizard's
 * review step displays the amount BEFORE the ticket exists, so it asks the
 * catalog-backed price instead of carrying a hardcoded figure.
 *
 * <p>Lives in the {@code admission} context (not next to
 * {@code ProgramAdmissionConfigController}) because pricing a ficha is an
 * admission concern and it depends on admission's payment-concept port — the
 * bounded contexts never import each other's use cases. The URL sits under
 * {@code /program-admission-configs} because the quote is keyed by the config
 * the applicant picked; {@code SecurityFilterConfig} grants it {@code permitAll}
 * alongside {@code /program-admission-configs/options}, since the wizard calls
 * it before the applicant has any session.
 */
@RestController
@RequestMapping("/program-admission-configs")
public class FichaAmountController {

	private final GetFichaAmountUseCase getFichaAmountUseCase;

	public FichaAmountController(GetFichaAmountUseCase getFichaAmountUseCase) {
		this.getFichaAmountUseCase = getFichaAmountUseCase;
	}

	/**
	 * {@code 200} with the catalog amount; {@code 404} when the config id is
	 * unknown; {@code 409} when the program has no active enrollment concept to
	 * price the ficha, or more than one (ambiguous price).
	 */
	@GetMapping("/{id}/ficha-amount")
	public ResponseEntity<FichaAmountResponse> fichaAmount(@PathVariable UUID id) {
		return ResponseEntity.ok(FichaAmountResponse.from(getFichaAmountUseCase.quote(id)));
	}
}
