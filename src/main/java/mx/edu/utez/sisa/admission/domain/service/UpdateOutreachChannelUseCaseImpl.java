package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.domain.port.in.CreateOutreachChannelUseCase.OutreachChannelResult;
import mx.edu.utez.sisa.admission.domain.port.in.UpdateOutreachChannelUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import mx.edu.utez.sisa.admission.shared.exception.DuplicateOutreachChannelNameException;
import mx.edu.utez.sisa.admission.shared.exception.OutreachChannelNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates an existing {@code OutreachChannel}'s {@code name}, revalidating the
 * uniqueness of the normalized name (Fase 9) the same way
 * {@code CreateOutreachChannelUseCaseImpl} does on the way in.
 */
public class UpdateOutreachChannelUseCaseImpl implements UpdateOutreachChannelUseCase {

	private final OutreachChannelRepository channelRepository;

	public UpdateOutreachChannelUseCaseImpl(OutreachChannelRepository channelRepository) {
		this.channelRepository = channelRepository;
	}

	@Override
	@Transactional
	public OutreachChannelResult updateChannel(UpdateOutreachChannelCommand command) {
		OutreachChannel channel = channelRepository.findById(command.channelId())
				.orElseThrow(() -> new OutreachChannelNotFoundException("Outreach channel not found: " + command.channelId()));

		// Normalizar antes de comprobar, por el mismo motivo que en create.
		String name = CatalogDisplayNameNormalizer.displayName(command.name());
		// La unicidad se revalida excluyendo esta misma fila: renombrar un canal
		// al nombre que ya tiene no es un conflicto, y sin el filtro el guardado
		// sin cambios de la lista devolvería 409. Mismo autocambio que en
		// UpdateGroupUseCaseImpl y UpdateAcademicDivisionUseCaseImpl.
		channelRepository.findByName(name)
				.filter(found -> !found.getId().equals(channel.getId())).ifPresent(found -> {
					throw new DuplicateOutreachChannelNameException(
							"Outreach channel name already in use: " + name);
				});

		channel.updateDetails(name);
		OutreachChannel saved = channelRepository.save(channel);

		return CreateOutreachChannelUseCaseImpl.toResult(saved);
	}
}
