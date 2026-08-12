package mx.edu.utez.sisa.shared.web;

import mx.edu.utez.sisa.shared.model.State;
import mx.edu.utez.sisa.shared.persistence.StateJpaRepository;
import mx.edu.utez.sisa.shared.web.dto.StateListItemResponse;
import mx.edu.utez.sisa.shared.web.dto.StateListResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only endpoint for the closed {@link State} catalog (plan:
 * {@code docs/plans/2026-07-28-inegi-catalogs-and-highschooltype.md}, section
 * 5): {@code GET /states} — full list, no pagination. No write endpoints
 * exist by design — the catalog is seeded once by
 * {@code StateAndMunicipalitySeedRunner} and never modified from the UI.
 *
 * <p>Lives in {@code shared.web} rather than any bounded context's own
 * {@code infrastructure.web} package: {@code State} is shared-kernel data
 * with no owning bounded context yet (Fase B's {@code Person} will be its
 * first real consumer), same placement rationale as {@code shared.model} and
 * {@code shared.web.dto.ErrorResponse}. Security: {@code authenticated()}
 * only, no role restriction — enforced by
 * {@code identity.SecurityFilterConfig}'s {@code /states} matcher, not here.
 */
@RestController
public class StateController {

	private final StateJpaRepository stateRepository;

	public StateController(StateJpaRepository stateRepository) {
		this.stateRepository = stateRepository;
	}

	@GetMapping("/states")
	public ResponseEntity<StateListResponse> listStates() {
		var items = stateRepository.findAllByOrderByNameAsc().stream().map(StateController::toItem).toList();
		return ResponseEntity.ok(new StateListResponse(items));
	}

	private static StateListItemResponse toItem(State state) {
		return new StateListItemResponse(state.getId(), state.getName(), state.getInegiCode());
	}
}
