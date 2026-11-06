package mx.edu.utez.sisa.admission.domain.port.out;

import java.util.UUID;

/** F1: the name a staff member's receipts show, e.g. who collected a cash payment. */
public interface StaffDirectoryPort {

	/** The person's full name, the username when there is none, or {@code null} for an unknown id. */
	String displayName(UUID userId);
}
