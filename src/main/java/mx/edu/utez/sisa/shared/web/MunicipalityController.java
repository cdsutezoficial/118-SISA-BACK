package mx.edu.utez.sisa.shared.web;

import mx.edu.utez.sisa.shared.model.Municipality;
import mx.edu.utez.sisa.shared.persistence.MunicipalityJpaRepository;
import mx.edu.utez.sisa.shared.web.dto.MunicipalityListItemResponse;
import mx.edu.utez.sisa.shared.web.dto.MunicipalityListResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Read-only endpoint for the closed {@link Municipality} catalog (plan:
 * {@code docs/plans/2026-07-28-inegi-catalogs-and-highschooltype.md}, section
 * 5): {@code GET /municipalities?stateId=} — {@code stateId} is a required
 * query param, no pagination. Same "seeded once, never modified from the UI"
 * shape and {@code shared.web} placement as {@link StateController}.
 * Missing {@code stateId} surfaces as HTTP 400 via
 * {@code identity.GlobalExceptionHandler}'s
 * {@code MissingServletRequestParameterException} handler. Security:
 * {@code authenticated()} only, no role restriction — enforced by
 * {@code identity.SecurityFilterConfig}'s {@code /municipalities} matcher.
 */
@RestController
public class MunicipalityController {

	private final MunicipalityJpaRepository municipalityRepository;

	public MunicipalityController(MunicipalityJpaRepository municipalityRepository) {
		this.municipalityRepository = municipalityRepository;
	}

	@GetMapping("/municipalities")
	public ResponseEntity<MunicipalityListResponse> listMunicipalities(@RequestParam UUID stateId) {
		var items = municipalityRepository.findByStateIdOrderByNameAsc(stateId).stream()
				.map(MunicipalityController::toItem).toList();
		return ResponseEntity.ok(new MunicipalityListResponse(items));
	}

	private static MunicipalityListItemResponse toItem(Municipality municipality) {
		return new MunicipalityListItemResponse(municipality.getId(), municipality.getName(),
				municipality.getInegiCode());
	}
}
