package mx.edu.utez.sisa.admission.infrastructure.web;

import mx.edu.utez.sisa.admission.domain.port.in.GetAdmissionDashboardUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.GetAdmissionDashboardUseCase.AdmissionDashboard;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Tablero de Admisión. Va fuera de {@code /candidates} a propósito: {@code GET
 * /candidates/*} es público (la ficha por su id) y {@code /candidates/dashboard}
 * caería en esa regla.
 */
@RestController
@RequestMapping("/admission/dashboard")
public class AdmissionDashboardController {

	private final GetAdmissionDashboardUseCase getAdmissionDashboardUseCase;

	public AdmissionDashboardController(GetAdmissionDashboardUseCase getAdmissionDashboardUseCase) {
		this.getAdmissionDashboardUseCase = getAdmissionDashboardUseCase;
	}

	@GetMapping
	public ResponseEntity<AdmissionDashboard> get(@RequestParam(required = false) UUID periodId,
			Authentication authentication) {
		return ResponseEntity
				.ok(getAdmissionDashboardUseCase.get(UUID.fromString(authentication.getName()), periodId));
	}
}
