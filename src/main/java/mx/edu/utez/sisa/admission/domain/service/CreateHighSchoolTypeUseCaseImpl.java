package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
import mx.edu.utez.sisa.admission.shared.exception.DuplicateHighSchoolTypeNameException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a {@code HighSchoolType} catalog entry, enforcing uniqueness of the
 * normalized {@code name} (Fase 10).
 *
 * <p>Same shape as {@code CreateOutreachChannelUseCaseImpl}, which is the sibling
 * this catalog was modelled on. The name is normalized by
 * {@link CatalogDisplayNameNormalizer} <b>before</b> the duplicate check and before
 * persisting: MySQL's {@code NO PAD} collation treats a trailing space as
 * significant, so comparing the raw value would let {@code "Conalep "} through.
 */
public class CreateHighSchoolTypeUseCaseImpl implements CreateHighSchoolTypeUseCase {

	private final HighSchoolTypeRepository highSchoolTypeRepository;

	public CreateHighSchoolTypeUseCaseImpl(HighSchoolTypeRepository highSchoolTypeRepository) {
		this.highSchoolTypeRepository = highSchoolTypeRepository;
	}

	@Override
	@Transactional
	public HighSchoolTypeResult createHighSchoolType(CreateHighSchoolTypeCommand command) {
		String name = CatalogDisplayNameNormalizer.displayName(command.name());
		requireUniqueName(name, highSchoolTypeRepository);

		HighSchoolType highSchoolType = new HighSchoolType(name);
		HighSchoolType saved = highSchoolTypeRepository.save(highSchoolType);

		return toResult(saved);
	}

	/**
	 * Unicidad del nombre normalizado. La restricción de la tabla y esta
	 * comprobación hacen falta las dos: el índice es el único que puede cerrar la
	 * carrera entre dos altas simultáneas, y esto es lo que produce el mensaje del
	 * 409 del módulo en vez del que arma Hibernate. La consulta corre sobre el
	 * valor ya normalizado, que es justo lo que el índice no sabe medir.
	 */
	static void requireUniqueName(String name, HighSchoolTypeRepository highSchoolTypeRepository) {
		if (highSchoolTypeRepository.findByName(name).isPresent()) {
			throw new DuplicateHighSchoolTypeNameException("High school type name already in use: " + name);
		}
	}

	static HighSchoolTypeResult toResult(HighSchoolType highSchoolType) {
		return new HighSchoolTypeResult(highSchoolType.getId(), highSchoolType.getName(), highSchoolType.getStatus());
	}
}
