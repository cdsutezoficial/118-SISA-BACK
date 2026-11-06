package mx.edu.utez.sisa.finance.infrastructure.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import mx.edu.utez.sisa.finance.domain.model.FinanceAlertStatus;
import mx.edu.utez.sisa.finance.domain.port.in.ManageFinanceAlertsUseCase;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.FinanceAlertResponse;
import mx.edu.utez.sisa.finance.infrastructure.web.dto.ResolveFinanceAlertRequest;

/**
 * The Finanzas review screen for payments that need a person (E4). Gated by the
 * {@code /finance/**} role matcher and the {@code FINANCE_ALERTS_*} permissions.
 */
@RestController
@RequestMapping("/finance/alerts")
public class FinanceAlertController {

	private final ManageFinanceAlertsUseCase manageFinanceAlertsUseCase;

	public FinanceAlertController(ManageFinanceAlertsUseCase manageFinanceAlertsUseCase) {
		this.manageFinanceAlertsUseCase = manageFinanceAlertsUseCase;
	}

	/** {@code status} filters; without it every alert comes back, newest first. */
	@GetMapping
	public ResponseEntity<List<FinanceAlertResponse>> list(@RequestParam(required = false) FinanceAlertStatus status) {
		return ResponseEntity.ok(manageFinanceAlertsUseCase.list(status).stream().map(FinanceAlertResponse::from)
				.toList());
	}

	@PatchMapping("/{id}/resolve")
	public ResponseEntity<FinanceAlertResponse> resolve(@PathVariable UUID id,
			@Valid @RequestBody(required = false) ResolveFinanceAlertRequest request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(authentication.getName());
		return ResponseEntity.ok(FinanceAlertResponse.from(manageFinanceAlertsUseCase.resolve(id, userId,
				request == null ? null : request.note())));
	}
}
