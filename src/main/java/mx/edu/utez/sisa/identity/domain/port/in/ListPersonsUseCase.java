package mx.edu.utez.sisa.identity.domain.port.in;

import java.util.List;
import java.util.UUID;

/**
 * Paginated, filterable query for existing {@code Person} records (plan:
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.2, back
 * office "buscar personas existentes" flow feeding both the standalone
 * Persons screen and the Person-selection step of the "Registrar Usuario"
 * wizard). Restricted to ADMIN or SERVICIOS_ESCOLARES callers, same split of
 * responsibilities as {@code ListUsersUseCase}: role authorization is
 * enforced by {@code SecurityFilterConfig}, this use case only asserts the
 * caller's mustChangePassword gate.
 */
public interface ListPersonsUseCase {

	ListPersonsResult listPersons(ListPersonsQuery query);

	/**
	 * @param callerId the acting user, used only for the mustChangePassword guard (role authorization is
	 *                 enforced by SecurityFilterConfig)
	 * @param search   optional free-text match against curp, full name, or institutionalEmail
	 * @param page     zero-based page index; negative values are normalized to 0
	 * @param size     page size; normalized to a minimum of 1 and capped at {@link #MAX_PAGE_SIZE}
	 */
	record ListPersonsQuery(UUID callerId, String search, int page, int size) {

		public static final int DEFAULT_PAGE_SIZE = 20;

		public static final int MAX_PAGE_SIZE = 100;
	}

	record ListPersonsResult(List<PersonSummary> persons, long totalElements, int totalPages, int page, int size) {
	}

	/**
	 * @param hasUser true if this Person already has a linked {@code User} account (plan 4.2: lets the
	 *                frontend distinguish Persons available for "Registrar Usuario" from ones that already
	 *                have an account)
	 */
	record PersonSummary(UUID personId, String curp, String firstName, String lastName1, String lastName2,
			String institutionalEmail, boolean hasUser) {
	}
}
