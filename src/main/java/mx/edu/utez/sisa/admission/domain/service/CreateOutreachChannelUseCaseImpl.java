package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.domain.port.in.CreateOutreachChannelUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import mx.edu.utez.sisa.admission.shared.exception.DuplicateOutreachChannelNameException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates an {@code OutreachChannel} catalog entry, enforcing uniqueness of the
 * normalized {@code name} (Fase 9).
 *
 * <p><b>This reverses an earlier decision.</b> The aggregate shipped with
 * explicitly no uniqueness on {@code name}, on the grounds that the domain doc
 * did not ask for it and a rule should not be invented. The plan of
 * 2026-10-02 does ask for it — "el nombre normalizado debe ser único entre
 * registros activos e inactivos" — so the constraint now exists and this javadoc
 * is the record of the change.
 *
 * <p>The name is normalized by {@link OutreachChannelTextNormalizer} <b>before</b>
 * the duplicate check and before persisting, so the comparison sees the same form
 * the column will hold. It has to be normalized first rather than rely on the
 * query alone: {@code findByNameIgnoreCase} is case-insensitive but not
 * whitespace-insensitive, so {@code " Facebook "} would sail past the check and
 * land next to {@code "Facebook"} — which is literally the first acceptance
 * criterion of this phase.
 */
public class CreateOutreachChannelUseCaseImpl implements CreateOutreachChannelUseCase {

	private final OutreachChannelRepository channelRepository;

	public CreateOutreachChannelUseCaseImpl(OutreachChannelRepository channelRepository) {
		this.channelRepository = channelRepository;
	}

	@Override
	@Transactional
	public OutreachChannelResult createChannel(CreateOutreachChannelCommand command) {
		String name = OutreachChannelTextNormalizer.name(command.name());
		requireUniqueName(name, channelRepository);

		OutreachChannel channel = new OutreachChannel(name);
		OutreachChannel saved = channelRepository.save(channel);

		return toResult(saved);
	}

	/**
	 * Unicidad del nombre normalizado. Se comprueba en Java además de por la
	 * restricción de la tabla, y las dos hacen falta por razones distintas: la
	 * restricción es la única que puede cerrar la carrera entre dos altas
	 * simultáneas, y esto es la que produce el mensaje del 409 de este módulo en
	 * vez del que arma Hibernate al vencer el índice. Lo que el índice de MySQL no
	 * puede cubrir es el whitespace ya normalizado —ver el javadoc de
	 * {@code OutreachChannel}— y esta comprobación corre sobre el valor
	 * normalizado, así que cierra esa mitad.
	 */
	static void requireUniqueName(String name, OutreachChannelRepository channelRepository) {
		if (channelRepository.findByName(name).isPresent()) {
			throw new DuplicateOutreachChannelNameException("Outreach channel name already in use: " + name);
		}
	}

	static OutreachChannelResult toResult(OutreachChannel channel) {
		return new OutreachChannelResult(channel.getId(), channel.getName(), channel.getStatus());
	}
}
