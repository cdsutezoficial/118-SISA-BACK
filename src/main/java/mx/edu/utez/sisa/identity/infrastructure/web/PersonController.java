package mx.edu.utez.sisa.identity.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.identity.domain.port.in.CreatePersonUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.CreatePersonUseCase.CreatePersonCommand;
import mx.edu.utez.sisa.identity.domain.port.in.CreatePersonUseCase.PersonResult;
import mx.edu.utez.sisa.identity.domain.port.in.ListPersonsUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.ListPersonsUseCase.ListPersonsQuery;
import mx.edu.utez.sisa.identity.domain.port.in.ListPersonsUseCase.ListPersonsResult;
import mx.edu.utez.sisa.identity.domain.port.in.ListPersonsUseCase.PersonSummary;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.CreatePersonRequest;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.PersonListItemResponse;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.PersonListResponse;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.PersonResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin controller for {@code POST /persons} and {@code GET /persons} (plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.1/4.2),
 * the manual internal-staff registration flow. {@code POST /persons} is
 * ADMIN-only; {@code GET /persons} additionally allows
 * SERVICIOS_ESCOLARES — both enforced by {@code SecurityFilterConfig}'s
 * {@code /persons} matchers, not here. Same shape as {@code UserController}:
 * the caller id used for the mustChangePassword guard is extracted from the
 * JWT principal, not the request body.
 */
@RestController
@RequestMapping("/persons")
public class PersonController {

	private final CreatePersonUseCase createPersonUseCase;
	private final ListPersonsUseCase listPersonsUseCase;

	public PersonController(CreatePersonUseCase createPersonUseCase, ListPersonsUseCase listPersonsUseCase) {
		this.createPersonUseCase = createPersonUseCase;
		this.listPersonsUseCase = listPersonsUseCase;
	}

	@PostMapping
	public ResponseEntity<PersonResponse> createPerson(@Valid @RequestBody CreatePersonRequest request) {
		var callerId = AuthenticatedCaller.currentUserId();
		PersonResult result = createPersonUseCase.createPerson(new CreatePersonCommand(callerId, request.curp(),
				request.firstName(), request.lastName1(), request.lastName2(), request.institutionalEmail()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
	}

	@GetMapping
	public ResponseEntity<PersonListResponse> listPersons(@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		var callerId = AuthenticatedCaller.currentUserId();
		ListPersonsResult result = listPersonsUseCase.listPersons(new ListPersonsQuery(callerId, search, page, size));
		return ResponseEntity.ok(new PersonListResponse(result.persons().stream().map(PersonController::toItem).toList(),
				result.totalElements(), result.totalPages(), result.page(), result.size()));
	}

	private static PersonResponse toResponse(PersonResult result) {
		return new PersonResponse(result.personId(), result.curp(), result.firstName(), result.lastName1(),
				result.lastName2(), result.institutionalEmail());
	}

	private static PersonListItemResponse toItem(PersonSummary summary) {
		return new PersonListItemResponse(summary.personId(), summary.curp(), summary.firstName(),
				summary.lastName1(), summary.lastName2(), summary.institutionalEmail(), summary.hasUser());
	}
}
