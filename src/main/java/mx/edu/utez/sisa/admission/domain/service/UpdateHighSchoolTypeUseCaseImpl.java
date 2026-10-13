package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
import mx.edu.utez.sisa.admission.domain.port.in.CreateHighSchoolTypeUseCase.HighSchoolTypeResult;
import mx.edu.utez.sisa.admission.domain.port.in.UpdateHighSchoolTypeUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
import mx.edu.utez.sisa.admission.shared.exception.DuplicateHighSchoolTypeNameException;
import mx.edu.utez.sisa.admission.shared.exception.HighSchoolTypeNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates an existing {@code HighSchoolType}'s {@code name}, revalidating the
 * uniqueness of the normalized name (Fase 10) the way
 * {@code CreateHighSchoolTypeUseCaseImpl} does on the way in.
 */
public class UpdateHighSchoolTypeUseCaseImpl implements UpdateHighSchoolTypeUseCase {

	private final HighSchoolTypeRepository highSchoolTypeRepository;

	public UpdateHighSchoolTypeUseCaseImpl(HighSchoolTypeRepository highSchoolTypeRepository) {
		this.highSchoolTypeRepository = highSchoolTypeRepository;
	}

	@Override
	@Transactional
	public HighSchoolTypeResult updateHighSchoolType(UpdateHighSchoolTypeCommand command) {
		HighSchoolType highSchoolType = highSchoolTypeRepository.findById(command.highSchoolTypeId())
				.orElseThrow(() -> new HighSchoolTypeNotFoundException(
						"High school type not found: " + command.highSchoolTypeId()));

		// Normalizar antes de comprobar, por el mismo motivo que en create.
		String name = CatalogDisplayNameNormalizer.displayName(command.name());
		// La unicidad se revalida excluyendo esta misma fila: guardar sin cambios
		// desde la lista no es un conflicto. Mismo autocambio que en
		// UpdateOutreachChannelUseCaseImpl y UpdateAcademicDivisionUseCaseImpl.
		highSchoolTypeRepository.findByName(name)
				.filter(found -> !found.getId().equals(highSchoolType.getId())).ifPresent(found -> {
					throw new DuplicateHighSchoolTypeNameException(
							"High school type name already in use: " + name);
				});

		highSchoolType.updateDetails(name);
		HighSchoolType saved = highSchoolTypeRepository.save(highSchoolType);

		return CreateHighSchoolTypeUseCaseImpl.toResult(saved);
	}
}
