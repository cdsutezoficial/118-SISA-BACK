package mx.edu.utez.sisa.finance.domain.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only out-port that navigates a payment anchor
 * ({@code candidateId}/{@code studentId}) to the {@code Person} behind it.
 *
 * <p>Since the 2026-10-21 reanchor a {@code Payment} no longer stores a
 * {@code personId}: it knows who is paying directly. Reporting (estado de
 * cuenta, recibos) still needs the person's name/CURP, so it reaches it by
 * navigating {@code candidate.personId} / {@code student.personId} through
 * this port instead of assuming the caller already holds a {@code personId}.
 */
public interface AnchorQueryPort {

	/**
	 * The person behind an aspirante, or empty when no such candidate exists.
	 */
	Optional<PersonQueryPort.PersonView> findPersonByCandidateId(UUID candidateId);

	/**
	 * The person behind a student, or empty when no such student exists.
	 */
	Optional<PersonQueryPort.PersonView> findPersonByStudentId(UUID studentId);

	/**
	 * Whether the candidate exists — the anchor validation the create/quote
	 * flows run before freezing an adeudo.
	 */
	boolean candidateExists(UUID candidateId);

	/**
	 * Whether the student exists.
	 */
	boolean studentExists(UUID studentId);
}
