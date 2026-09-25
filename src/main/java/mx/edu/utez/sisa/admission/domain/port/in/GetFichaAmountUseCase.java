package mx.edu.utez.sisa.admission.domain.port.in;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Quotes the admission-ficha price for a program's open sales window (Fase 11).
 * The registration wizard's review step shows the amount BEFORE the ticket
 * exists, so it needs the catalog price up front — served by the public
 * {@code GET /program-admission-configs/{id}/ficha-amount} endpoint.
 *
 * <p>Priced by {@code FichaAmountResolver} from the program's active
 * {@code ENROLLMENT} concept, the very same rule the registration command
 * persists on the ticket, so the previewed amount is always the charged one.
 */
public interface GetFichaAmountUseCase {

	FichaAmountQuote quote(UUID admissionConfigId);

	/**
	 * @param amount      what the applicant will have to pay
	 * @param conceptName the catalog concept that prices the ficha
	 * @param programName the program the quoted config belongs to
	 */
	record FichaAmountQuote(BigDecimal amount, String conceptName, String programName) {
	}
}
