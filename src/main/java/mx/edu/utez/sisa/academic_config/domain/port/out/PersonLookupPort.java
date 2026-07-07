package mx.edu.utez.sisa.academic_config.domain.port.out;

import java.util.UUID;

/**
 * Read-only out-port over the shared-kernel {@code Person} table, used only
 * to validate {@code directorPersonId} references (design.md — Decision:
 * Director validation via own out-port, not {@code identity.PersonRepository}
 * — cross-module repository access is forbidden by {@code config.yaml}, so
 * this module defines its own minimal query port over the shared
 * {@code person} table instead of importing identity's port). Deliberately
 * minimal: {@code academic_config} never needs the full {@code Person}
 * shape, only presence.
 */
public interface PersonLookupPort {

	boolean existsById(UUID personId);
}
