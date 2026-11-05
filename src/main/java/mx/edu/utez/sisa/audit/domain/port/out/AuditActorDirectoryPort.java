package mx.edu.utez.sisa.audit.domain.port.out;

import java.util.UUID;

/** El nombre de quien hizo el cambio, para guardarlo como foto junto a su id. */
public interface AuditActorDirectoryPort {

	/** {@code null} si no hay usuario o no se encuentra. */
	String displayName(UUID userId);
}
