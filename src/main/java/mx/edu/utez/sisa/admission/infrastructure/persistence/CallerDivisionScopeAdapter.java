package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.port.out.CallerDivisionScopePort;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Implements {@link CallerDivisionScopePort} over {@code user_role}/{@code role}.
 *
 * <p>Only {@code DIRECTOR_DIVISION} is treated as division-scoped here. The
 * coarse security matcher already guarantees the caller is ADMIN,
 * SERVICIOS_ESCOLARES or DIRECTOR_DIVISION, so "not a Director" unambiguously
 * means the unrestricted pair.
 */
@Component
public class CallerDivisionScopeAdapter implements CallerDivisionScopePort {

	private static final String DIRECTOR_ROLE_KEY = RoleType.DIRECTOR_DIVISION.name();

	private final CallerUserRoleJpaRepository userRoleJpaRepository;

	public CallerDivisionScopeAdapter(CallerUserRoleJpaRepository userRoleJpaRepository) {
		this.userRoleJpaRepository = userRoleJpaRepository;
	}

	@Override
	public DivisionScope resolve(UUID callerId) {
		List<UserRole> grants = userRoleJpaRepository.findGrantsByUserAndRoleKey(callerId, DIRECTOR_ROLE_KEY);
		if (grants.isEmpty()) {
			return DivisionScope.unrestricted();
		}
		// A Director with no division is still division-scoped — to a division
		// that does not exist. The use case turns that into an empty page (fail
		// closed) instead of granting sight of every candidate.
		return DivisionScope.director(grants.get(0).getDivisionId());
	}
}
