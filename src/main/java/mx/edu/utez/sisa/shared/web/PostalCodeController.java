package mx.edu.utez.sisa.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import mx.edu.utez.sisa.shared.model.Municipality;
import mx.edu.utez.sisa.shared.model.PostalCodeSettlement;
import mx.edu.utez.sisa.shared.model.State;
import mx.edu.utez.sisa.shared.persistence.MunicipalityJpaRepository;
import mx.edu.utez.sisa.shared.persistence.PostalCodeSettlementJpaRepository;
import mx.edu.utez.sisa.shared.persistence.StateJpaRepository;
import mx.edu.utez.sisa.shared.web.dto.ErrorResponse;
import mx.edu.utez.sisa.shared.web.dto.PostalCodeLookupItemResponse;
import mx.edu.utez.sisa.shared.web.dto.PostalCodeLookupResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Read-only public lookup consumed by the admission wizard.
 *
 * <p>The endpoint owns the applicant-facing copy: it returns a
 * {@code message} that describes the outcome (found N settlements / none
 * found) and answers malformed requests with the standard
 * {@link ErrorResponse} envelope. No technical detail (SQL, ids, stack traces)
 * ever leaves this class.
 */
@RestController
public class PostalCodeController {

	private static final Logger log = LoggerFactory.getLogger(PostalCodeController.class);

	private static final Pattern POSTAL_CODE_FORMAT = Pattern.compile("\\d{5}");

	private static final String INVALID_FORMAT_MESSAGE = "El código postal debe tener 5 dígitos.";

	private static final String NOT_FOUND_MESSAGE =
			"No encontramos colonias registradas para este código postal. Puedes capturar tu domicilio manualmente.";

	private final PostalCodeSettlementJpaRepository postalCodeSettlementRepository;

	private final StateJpaRepository stateRepository;

	private final MunicipalityJpaRepository municipalityRepository;

	public PostalCodeController(PostalCodeSettlementJpaRepository postalCodeSettlementRepository,
			StateJpaRepository stateRepository, MunicipalityJpaRepository municipalityRepository) {
		this.postalCodeSettlementRepository = postalCodeSettlementRepository;
		this.stateRepository = stateRepository;
		this.municipalityRepository = municipalityRepository;
	}

	@GetMapping("/postal-codes/{postalCode}")
	public ResponseEntity<?> lookup(@PathVariable String postalCode, HttpServletRequest request) {
		if (!POSTAL_CODE_FORMAT.matcher(postalCode).matches()) {
			log.info("Consulta de código postal rechazada: el valor recibido no tiene 5 dígitos");
			return badRequest(request);
		}

		List<PostalCodeLookupItemResponse> items = new ArrayList<>();
		int skipped = 0;
		for (PostalCodeSettlement settlement : postalCodeSettlementRepository
				.findByPostalCodeOrderBySettlementNameAsc(postalCode)) {
			var state = stateRepository.findById(settlement.getStateId()).orElse(null);
			var municipality = municipalityRepository.findById(settlement.getMunicipalityId()).orElse(null);
			if (state == null || municipality == null) {
				skipped++;
				continue;
			}
			items.add(toItem(settlement, state, municipality));
		}

		if (skipped > 0) {
			log.warn("Consulta de código postal {}: se omitieron {} asentamientos por referencias de estado o municipio inexistentes",
					postalCode, skipped);
		}
		log.debug("Consulta de código postal {}: {} asentamientos encontrados", postalCode, items.size());

		return ResponseEntity.ok(new PostalCodeLookupResponse(postalCode, messageFor(items.size()), items));
	}

	private static PostalCodeLookupItemResponse toItem(PostalCodeSettlement settlement, State state,
			Municipality municipality) {
		String localityName = settlement.getLocalityName() != null && !settlement.getLocalityName().isBlank()
				? settlement.getLocalityName()
				: municipality.getName();
		return new PostalCodeLookupItemResponse(settlement.getSettlementName(), localityName, state.getName(),
				municipality.getName());
	}

	private static String messageFor(int count) {
		if (count == 0) {
			return NOT_FOUND_MESSAGE;
		}
		if (count == 1) {
			return "Se encontró una colonia para este código postal.";
		}
		return "Se encontraron " + count + " colonias para este código postal. Selecciona la tuya.";
	}

	private static ResponseEntity<ErrorResponse> badRequest(HttpServletRequest request) {
		return ResponseEntity.badRequest()
				.body(new ErrorResponse(Instant.now(), HttpStatus.BAD_REQUEST.value(), "Solicitud inválida",
						INVALID_FORMAT_MESSAGE, request.getRequestURI()));
	}
}
