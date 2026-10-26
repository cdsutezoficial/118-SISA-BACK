package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * Full account statement ({@code GET /finance/account-statement}): the
 * candidato/estudiante anchor and every one of their adeudos with abonos.
 */
public record AccountStatementResponse(UUID candidateId, UUID studentId, String personName,
		List<AccountStatementPaymentResponse> payments) {
}
