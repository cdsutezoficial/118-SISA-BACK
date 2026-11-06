package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import jakarta.validation.constraints.Size;

/** What Finanzas did about the alert; optional. */
public record ResolveFinanceAlertRequest(@Size(max = 500) String note) {
}
