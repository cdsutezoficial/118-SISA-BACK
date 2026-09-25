package mx.edu.utez.sisa.admission.domain.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only out-port resolving INEGI {@code State}/{@code Municipality} NAMES
 * from their ids for the ficha projection (PDF / confirmation emails) — the
 * shared kernel stores those references as bare {@code UUID} columns, and the
 * admissible context renders their display names via this tiny lookup instead
 * of importing the shared persistence layer directly (same "own minimal
 * access" rationale as {@code ProgramAdmissionConfigQueryPort}). The
 * {@code shared} catalogs are closed, seed-only reference data, so only reads
 * are exposed.
 */
public interface PlaceNameLookupPort {

	/** The federal-entity name for {@code stateId}, or empty if unknown. */
	Optional<String> findStateName(UUID stateId);

	/** The municipality name for {@code municipalityId}, or empty if unknown. */
	Optional<String> findMunicipalityName(UUID municipalityId);
}