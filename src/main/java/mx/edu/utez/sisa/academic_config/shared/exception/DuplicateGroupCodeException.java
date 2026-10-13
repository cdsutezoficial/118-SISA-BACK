package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when {@code CreateGroupUseCase}/{@code UpdateGroupUseCase} is invoked
 * with a {@code code} already used by another {@code Group} of the SAME
 * {@code generationId}. Maps to HTTP 409 in the web layer's
 * {@code GlobalExceptionHandler} — same pattern as
 * {@code DuplicateGenerationNumberException}.
 *
 * <p>
 * The uniqueness key is {@code (generationId, code)}, NOT
 * {@code (programId, generationId, code)} as the Fase 8 plan of
 * {@code md/pendientes/2026-10-02-configuracion-academica-y-catalogos.md}
 * words it. That document lists "carrera/programa + generación + letra", but
 * {@code programId} is a denormalized copy of {@code generationId}'s owning
 * {@code Generation.programId} (see {@code Group}'s class javadoc), so it is
 * functionally dependent on {@code generationId} and adds nothing to the key:
 * given a {@code generationId}, the program is already fixed. Including it
 * would only make the index wider.
 *
 * <p>
 * {@code maxCapacity} is deliberately NOT part of the key, per the same
 * document: capacity is mutable, and making it unique would prevent two
 * identical sections of the same generation and level from existing.
 *
 * <p>
 * {@code periodId} is also NOT part of the key, and that is a deliberate
 * consequence of the domain: a group spans a generation, not a period. As a
 * generation advances, the SAME group (same code, same level) moves to the next
 * {@code periodId} rather than a new group being created — that is exactly what
 * the automatic "heredar el grupo del nivel siguiente" rule in
 * {@code 02-config-academica.md} does. If {@code periodId} were part of the
 * key, the natural state of a generation (one group per level, present in every
 * period) would have to be re-created by hand every period.
 */
public class DuplicateGroupCodeException extends RuntimeException {

	public DuplicateGroupCodeException(String message) {
		super(message);
	}
}