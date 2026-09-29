package mx.edu.utez.sisa.academic_config.domain.port.out;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentArea;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentAreaStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link PaymentArea}, mirroring
 * {@code AcademicDivisionRepository}'s shape: both {@code name} and
 * {@code code} are unique business keys, so it exposes {@code findByCode} /
 * {@code findByName}.
 */
public interface PaymentAreaRepository {

	PaymentArea save(PaymentArea area);

	Optional<PaymentArea> findById(UUID id);

	/**
	 * Case-insensitive lookup by {@code code}. Case-insensitivity is the
	 * adapter's responsibility (e.g. {@code findByCodeIgnoreCase} on the
	 * Spring Data repository); this port only declares the contract.
	 */
	Optional<PaymentArea> findByCode(String code);

	/**
	 * Case-insensitive lookup by {@code name} — see {@link #findByCode(String)}.
	 */
	Optional<PaymentArea> findByName(String name);

	/**
	 * Filterable, paginated query backing {@code ListPaymentAreasUseCase}.
	 */
	PaymentAreaSearchPage search(PaymentAreaSearchCriteria criteria);

	/**
	 * @param status optional — filters to areas with this exact status
	 * @param search optional free-text match against {@code name} or {@code code}
	 * @param page   zero-based page index
	 * @param size   page size
	 */
	record PaymentAreaSearchCriteria(PaymentAreaStatus status, String search, int page, int size) {
	}

	/**
	 * @param content       the {@link PaymentArea} rows for the requested page
	 * @param totalElements total matching rows across all pages
	 * @param totalPages    total page count for {@code totalElements} at the requested page size
	 */
	record PaymentAreaSearchPage(List<PaymentArea> content, long totalElements, int totalPages) {
	}
}
