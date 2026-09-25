package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.port.out.PlaceNameLookupPort;
import mx.edu.utez.sisa.shared.persistence.MunicipalityJpaRepository;
import mx.edu.utez.sisa.shared.persistence.StateJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link PlaceNameLookupPort} adapter delegating to the shared
 * {@link StateJpaRepository}/{@link MunicipalityJpaRepository} — the INEGI
 * state/municipality catalogs are closed, seed-only reference data with no
 * business logic, so this is a thin read-only mapping to the ficha projection.
 */
@Component
public class PlaceNameLookupAdapter implements PlaceNameLookupPort {

	private final StateJpaRepository stateJpaRepository;

	private final MunicipalityJpaRepository municipalityJpaRepository;

	public PlaceNameLookupAdapter(StateJpaRepository stateJpaRepository,
			MunicipalityJpaRepository municipalityJpaRepository) {
		this.stateJpaRepository = stateJpaRepository;
		this.municipalityJpaRepository = municipalityJpaRepository;
	}

	@Override
	public Optional<String> findStateName(UUID stateId) {
		return stateJpaRepository.findById(stateId).map(state -> state.getName());
	}

	@Override
	public Optional<String> findMunicipalityName(UUID municipalityId) {
		return municipalityJpaRepository.findById(municipalityId).map(municipality -> municipality.getName());
	}
}