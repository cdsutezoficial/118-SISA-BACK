package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.RegisterCandidateCommand;
import mx.edu.utez.sisa.admission.domain.port.in.UpdateCandidateUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.CandidatePersonRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyExistsException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.HighSchoolTypeNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.InvalidCandidateFichaDataException;
import mx.edu.utez.sisa.admission.shared.exception.OutreachChannelNotFoundException;
import mx.edu.utez.sisa.shared.model.Person;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public class UpdateCandidateUseCaseImpl implements UpdateCandidateUseCase {

	private final CandidateRepository candidateRepository;

	private final CandidatePersonRepository candidatePersonRepository;

	private final OutreachChannelRepository outreachChannelRepository;

	private final HighSchoolTypeRepository highSchoolTypeRepository;

	public UpdateCandidateUseCaseImpl(CandidateRepository candidateRepository,
			CandidatePersonRepository candidatePersonRepository, OutreachChannelRepository outreachChannelRepository,
			HighSchoolTypeRepository highSchoolTypeRepository) {
		this.candidateRepository = candidateRepository;
		this.candidatePersonRepository = candidatePersonRepository;
		this.outreachChannelRepository = outreachChannelRepository;
		this.highSchoolTypeRepository = highSchoolTypeRepository;
	}

	@Override
	@Transactional
	public void update(UUID candidateId, RegisterCandidateCommand command) {
		Candidate candidate = candidateRepository.findById(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException("No existe el candidato: " + candidateId));
		Person person = candidatePersonRepository.findById(candidate.getPersonId())
				.orElseThrow(() -> new CandidateNotFoundException(
						"No existe la persona del candidato: " + candidateId));

		validate(candidate, person, command);

		CandidatePersonMapper.apply(person, command);
		candidatePersonRepository.save(person);

		candidate.updateSelection(command.seleccionCarrera().outreachChannelId(),
				command.seleccionCarrera().isFirstChoice());
		candidateRepository.save(candidate);
	}

	private void validate(Candidate candidate, Person person, RegisterCandidateCommand command) {
		UUID requestedConfig = command.seleccionCarrera().admissionConfigId();
		if (requestedConfig != null && !requestedConfig.equals(candidate.getAdmissionConfigId())) {
			throw new InvalidCandidateFichaDataException(
					"El cambio de carrera todavía no está disponible desde la edición de la ficha.");
		}

		// La CURP identifica a la persona: si se corrige, no puede quedar igual a
		// la de otra persona ya registrada (sería fusionar dos expedientes).
		Optional<Person> owner = candidatePersonRepository.findByCurp(command.datosGenerales().curp());
		if (owner.isPresent() && !owner.get().getId().equals(person.getId())) {
			throw new CandidateAlreadyExistsException("La CURP " + command.datosGenerales().curp()
					+ " ya está registrada a nombre de otra persona.");
		}

		UUID channelId = command.seleccionCarrera().outreachChannelId();
		if (channelId != null && outreachChannelRepository.findById(channelId).isEmpty()) {
			throw new OutreachChannelNotFoundException("No existe el canal de difusión: " + channelId);
		}

		UUID schoolTypeId = command.antecedentesEscolares() == null ? null
				: command.antecedentesEscolares().schoolTypeId();
		if (schoolTypeId != null && highSchoolTypeRepository.findById(schoolTypeId).isEmpty()) {
			throw new HighSchoolTypeNotFoundException("No existe el tipo de preparatoria: " + schoolTypeId);
		}
	}
}
